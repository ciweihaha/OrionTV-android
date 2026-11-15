package com.ciweihaha.oriontv.ui.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ciweihaha.oriontv.R
import com.ciweihaha.oriontv.data.source.LiveLines
import com.ciweihaha.oriontv.databinding.ActivityPlayerBinding

/**
 * Player (Media3 ExoPlayer) with the approved T5–T7 behaviours:
 *
 * - T5: skip-intro button is always visible while inside the intro window
 *   and shows the remaining seconds; tapping jumps past the intro.
 *   (Real intro detection is stage 2; stage 1 takes the window from the
 *   intent extra, or hides the button when it is 0.)
 * - T6: while the control bar is visible, DPAD left/right seeks directly.
 * - T7: left/right first shows a preview bubble (direction + landing
 *   time) in 10s steps; DPAD_CENTER commits the jump, BACK cancels,
 *   and 2s of no input auto-commits so the player never looks stuck.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var skipIntroEndMs: Long = 0L
    private var baseTitle: String = ""
    private var lineUrls: List<String> = emptyList()
    private var lineIndex: Int = 0
    private val seekPreview get() = _seekPreview
    private var _seekPreview: SeekPreviewController? = null

    private val handler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            updateProgressUi()
            handler.postDelayed(this, 500)
        }
    }
    private val hideControlsRunnable = Runnable { hideControls() }
    private val autoCommitRunnable = Runnable { commitPendingSeek() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        skipIntroEndMs = intent.getLongExtra(EXTRA_SKIP_INTRO_END_MS, 0L)
        baseTitle = title
        lineUrls = intent.getStringArrayListExtra(EXTRA_LINE_URLS)
            ?.filter { it.isNotBlank() }.orEmpty()
        lineIndex = intent.getIntExtra(EXTRA_LINE_INDEX, 0)
            .coerceIn(0, (lineUrls.size - 1).coerceAtLeast(0))
        if (url.isEmpty()) {
            Toast.makeText(this, "没有可播放的地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        binding.seekBar.isFocusable = false // progress display only; seek via preview flow
        binding.playPauseButton.setOnClickListener { togglePlayPause() }
        binding.skipIntroButton.setOnClickListener {
            player?.seekTo(skipIntroEndMs)
            binding.skipIntroButton.visibility = View.GONE
            showControls()
        }
        binding.lineSwitchButton.setOnClickListener {
            switchToLine(LiveLines.nextIndex(lineIndex, lineUrls.size))
        }
        updateTitleAndLineButton()
        initPlayer(url)
        handler.post(progressTicker)
        showControls()
    }

    private fun initPlayer(url: String) {
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        binding.playerView.player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "playback error", error)
                Toast.makeText(
                    this@PlayerActivity,
                    "播放失败：${error.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        })
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    _seekPreview = SeekPreviewController(exo.duration.coerceAtLeast(0L))
                }
            }
        })
        // The progress ticker is posted once in onCreate, not here, so a
        // line switch (which re-inits the player) does not double-post it.
    }

    /**
     * Multi-line (round 9): rebuild the player on the next line URL.
     * The title and the line button update to the new line; the progress
     * ticker keeps running (it guards on a null player).
     */
    private fun switchToLine(index: Int) {
        if (lineUrls.size <= 1 || index !in lineUrls.indices) return
        lineIndex = index
        player?.release()
        player = null
        _seekPreview = null
        initPlayer(lineUrls[index])
        updateTitleAndLineButton()
        Toast.makeText(this, "已切换到线路 ${index + 1}", Toast.LENGTH_SHORT).show()
        showControls()
    }

    /** Title + line button: the button only shows when there are 2+ lines. */
    private fun updateTitleAndLineButton() {
        if (lineUrls.size > 1) {
            binding.playerTitle.text = "$baseTitle · 线路 ${lineIndex + 1}"
            binding.lineSwitchButton.text = "线路 ${lineIndex + 1}/${lineUrls.size}"
            binding.lineSwitchButton.visibility = View.VISIBLE
        } else {
            binding.playerTitle.text = baseTitle
            binding.lineSwitchButton.visibility = View.GONE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val exo = player
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (binding.controlsBar.visibility != View.VISIBLE) {
                    showControls()
                    return true
                }
                if (exo == null) return true
                val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                val target = seekPreview?.nudge(exo.currentPosition, direction)
                    ?: exo.currentPosition
                showSeekPreview(direction, target)
                handler.removeCallbacks(autoCommitRunnable)
                handler.postDelayed(autoCommitRunnable, SeekPreviewController.AUTO_COMMIT_MS)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (seekPreview?.hasPending == true) {
                    commitPendingSeek()
                } else {
                    togglePlayPause()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (seekPreview?.hasPending == true) {
                    seekPreview?.cancel()
                    binding.seekPreviewBubble.visibility = View.GONE
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            else -> {
                showControls()
                super.onKeyDown(keyCode, event)
            }
        }
    }

    private fun showSeekPreview(direction: Int, targetMs: Long) {
        val arrow = if (direction > 0) "快进" else "快退"
        binding.seekPreviewBubble.text =
            "$arrow 10 秒 · 落点 ${formatTime(targetMs)}"
        binding.seekPreviewBubble.visibility = View.VISIBLE
    }

    private fun commitPendingSeek() {
        val target = seekPreview?.commit() ?: return
        player?.seekTo(target)
        binding.seekPreviewBubble.visibility = View.GONE
        updateProgressUi()
    }

    private fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
        updateProgressUi()
        showControls()
    }

    private fun updateProgressUi() {
        val exo = player ?: return
        val position = exo.currentPosition
        val duration = exo.duration.coerceAtLeast(0L)
        binding.currentTime.text = formatTime(position)
        binding.durationTime.text = formatTime(duration)
        binding.seekBar.max = duration.toInt()
        binding.seekBar.progress = position.toInt()
        binding.playPauseButton.text = if (exo.isPlaying) "❚❚" else "▶"
        if (skipIntroEndMs > 0 && position < skipIntroEndMs) {
            val remaining = (skipIntroEndMs - position) / 1000
            binding.skipIntroButton.visibility = View.VISIBLE
            binding.skipIntroButton.text = "跳过片头 · 还剩 ${remaining}s"
        } else {
            binding.skipIntroButton.visibility = View.GONE
        }
    }

    private fun showControls() {
        binding.controlsBar.visibility = View.VISIBLE
        binding.playerTitle.visibility = View.VISIBLE
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT_MS)
    }

    private fun hideControls() {
        if (seekPreview?.hasPending == true) return // keep bar while previewing
        binding.controlsBar.visibility = View.GONE
        binding.playerTitle.visibility = View.GONE
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "--:--"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(progressTicker)
        handler.removeCallbacks(hideControlsRunnable)
        handler.removeCallbacks(autoCommitRunnable)
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SKIP_INTRO_END_MS = "skip_intro_end_ms"
        /** All playable line URLs of the current channel (round 9). */
        const val EXTRA_LINE_URLS = "line_urls"
        /** Index into [EXTRA_LINE_URLS] of the line being played. */
        const val EXTRA_LINE_INDEX = "line_index"
        private const val TAG = "PlayerActivity"
        private const val CONTROLS_TIMEOUT_MS = 4_000L
    }
}
