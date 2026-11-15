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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ciweihaha.oriontv.R
import com.ciweihaha.oriontv.data.AppSources
import com.ciweihaha.oriontv.data.model.WatchRecord
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
    /** URL + position of the current playback, kept across onStop/onStart
     *  (S1): releasing the player in onStop must not lose them, or the
     *  screen is dead after one HOME press. */
    private var currentUrl: String = ""
    private var savedPositionMs: Long = 0L
    private var savedPlayWhenReady: Boolean = true
    /** Resume (round 12): which VOD episode is playing; blank for live (no record). */
    private var vodId: String = ""
    private var vodTitle: String = ""
    private var episodeIndex: Int = 1
    private var episodeTitle: String = ""
    /** Wall-clock of the last periodic progress save (throttle, 30s). */
    private var lastProgressSaveMs: Long = 0L
    private val seekPreview get() = _seekPreview
    private var _seekPreview: SeekPreviewController? = null

    private val handler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            updateProgressUi()
            // Resume: persist progress every 30s while playing, so a kill
            // (not just BACK/HOME) still leaves a usable breakpoint.
            val exo = player
            if (exo != null && exo.isPlaying &&
                System.currentTimeMillis() - lastProgressSaveMs >= PROGRESS_SAVE_INTERVAL_MS
            ) {
                saveWatchProgress()
            }
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
        vodId = intent.getStringExtra(EXTRA_VOD_ID).orEmpty()
        vodTitle = intent.getStringExtra(EXTRA_VOD_TITLE).orEmpty()
        episodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, 1)
        episodeTitle = intent.getStringExtra(EXTRA_EPISODE_TITLE).orEmpty()
        // Resume: already thresholded by WatchRecord.resumePositionMs()
        // on the detail side; 0 means start from the beginning.
        val resumeStartMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
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
        currentUrl = url
        initPlayer(url, resumeStartMs)
        handler.post(progressTicker)
        showControls()
    }

    private fun initPlayer(url: String, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        binding.playerView.player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // D2: the raw message is often a wall of text (full URL,
                // nested causes) that a Toast cuts off mid-sentence.
                // Show a short mapped copy; the full detail stays in
                // Logcat where it can actually be read.
                Log.e(TAG, "playback error", error)
                Toast.makeText(
                    this@PlayerActivity,
                    shortPlaybackError(error),
                    Toast.LENGTH_LONG,
                ).show()
            }
        })
        exo.setMediaItem(MediaItem.fromUri(url))
        if (startPositionMs > 0) exo.seekTo(startPositionMs)
        exo.prepare()
        exo.playWhenReady = playWhenReady
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    // D3: no seek preview for live streams (see
                    // [isLiveStream]): their duration is TIME_UNSET, so
                    // a preview built here could only ever target 0.
                    _seekPreview = if (isLiveStream(exo)) {
                        null
                    } else {
                        SeekPreviewController(exo.duration.coerceAtLeast(0L))
                    }
                }
                // Resume: playback reached the end — save once so the
                // finished record is cleared and the episode restarts
                // from 0 next time (LibraryStore drops near-end records).
                if (state == Player.STATE_ENDED) saveWatchProgress()
            }
        })
        // The progress ticker is posted once in onCreate, not here, so a
        // line switch (which re-inits the player) does not double-post it.
    }

    /**
     * Multi-line (round 9, M9): try the new line on a fresh player FIRST
     * and only release the old one once the new player reports READY.
     * If the new line errors (or stalls past the timeout) the candidate
     * is released and the old line keeps playing — the previous code
     * released the old player first, so a failed switch lost playback.
     */
    private fun switchToLine(index: Int) {
        if (lineUrls.size <= 1 || index !in lineUrls.indices) return
        if (index == lineIndex) return
        resetSeekPreview()
        val oldPlayer = player
        val candidate = ExoPlayer.Builder(this).build()
        var settled = false
        val timeoutRunnable = Runnable {
            if (!settled) {
                settled = true
                candidate.release()
                if (!isDestroyed) {
                    Toast.makeText(
                        this@PlayerActivity,
                        "线路 ${index + 1} 切换超时，已保留当前线路",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
        candidate.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !settled) {
                    settled = true
                    handler.removeCallbacks(timeoutRunnable)
                    if (isDestroyed) {
                        candidate.release()
                        return
                    }
                    oldPlayer?.release()
                    player = candidate
                    binding.playerView.player = candidate
                    lineIndex = index
                    currentUrl = lineUrls[index]
                    savedPositionMs = 0L
                    // D3: same live guard as initPlayer — a live line
                    // has no seekable duration.
                    _seekPreview = if (isLiveStream(candidate)) {
                        null
                    } else {
                        SeekPreviewController(candidate.duration.coerceAtLeast(0L))
                    }
                    updateTitleAndLineButton()
                    Toast.makeText(this@PlayerActivity, "已切换到线路 ${index + 1}", Toast.LENGTH_SHORT).show()
                    showControls()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (settled) return
                settled = true
                handler.removeCallbacks(timeoutRunnable)
                candidate.release()
                if (isDestroyed) return
                // The old player is untouched and still attached, so
                // playback continues on the previous line.
                Log.e(TAG, "line switch failed", error)
                Toast.makeText(
                    this@PlayerActivity,
                    "线路 ${index + 1} 切换失败，已保留当前线路",
                    Toast.LENGTH_LONG,
                ).show()
                showControls()
            }
        })
        candidate.setMediaItem(MediaItem.fromUri(lineUrls[index]))
        candidate.prepare()
        candidate.playWhenReady = true
        handler.postDelayed(timeoutRunnable, LINE_SWITCH_TIMEOUT_MS)
    }

    /** Clear a pending seek preview: its target belongs to the old line. */
    private fun resetSeekPreview() {
        _seekPreview?.cancel()
        _seekPreview = null
        handler.removeCallbacks(autoCommitRunnable)
        binding.seekPreviewBubble.visibility = View.GONE
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
                // D3: live streams have no seek preview (it is null —
                // see initPlayer): the old code fell back to the
                // current position and showed a "落点 00:00" bubble
                // that auto-committed seekTo(0), throwing the viewer
                // to the start of the DVR window. Just show controls.
                val preview = seekPreview ?: return true
                val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                val target = preview.nudge(exo.currentPosition, direction)
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

    /**
     * Resume (round 12): persist VOD progress so reopening the episode
     * continues where the viewer left off. Live streams (no vodId) and
     * unknown durations are never recorded; a finished episode is
     * cleared by [com.ciweihaha.oriontv.data.LibraryStore.saveProgress]
     * (position within 60s of the end). Called on a 30s tick while
     * playing, on STATE_ENDED, and in onStop (BACK / HOME).
     */
    private fun saveWatchProgress() {
        if (vodId.isBlank()) return
        val exo = player ?: return
        val duration = exo.duration
        if (duration <= 0L) return // live / unknown duration: not a VOD record
        val position = exo.currentPosition
        if (position <= 0L) return
        lastProgressSaveMs = System.currentTimeMillis()
        AppSources.library(this).saveProgress(
            WatchRecord(
                vodId = vodId,
                title = vodTitle.ifBlank { baseTitle },
                episodeIndex = episodeIndex,
                episodeTitle = episodeTitle,
                positionMs = position,
                durationMs = duration,
            ),
        )
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

    /**
     * D3: a live stream has no seekable duration — ExoPlayer reports
     * [C.TIME_UNSET] and flags the item live. Seeking such a stream
     * is meaningless, so the preview flow is disabled for it.
     */
    private fun isLiveStream(exo: ExoPlayer): Boolean =
        exo.isCurrentMediaItemLive || exo.duration == C.TIME_UNSET

    /**
     * D2: one short, readable line per failure kind. The raw
     * [PlaybackException.message] (URL + cause chain) is logged, not
     * shown — a Toast cuts it off mid-sentence.
     */
    private fun shortPlaybackError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        -> "播放失败：网络异常，请检查网络后重试"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        -> "播放失败：找不到播放地址"
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        -> "播放失败：设备无法解码该视频"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> "播放失败：视频格式不支持"
        else -> "播放失败，请稍后重试"
    }

    /**
     * S1: onStop releases the player (required for background playback
     * hygiene), but the URL + position + play state are saved first and
     * onStart rebuilds the player from them — otherwise pressing HOME
     * once leaves a dead screen with no player at all.
     */
    override fun onStart() {
        super.onStart()
        if (player == null && currentUrl.isNotEmpty() && !isFinishing) {
            initPlayer(currentUrl, savedPositionMs, savedPlayWhenReady)
            handler.post(progressTicker)
            showControls()
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(progressTicker)
        handler.removeCallbacks(hideControlsRunnable)
        handler.removeCallbacks(autoCommitRunnable)
        savedPositionMs = player?.currentPosition ?: savedPositionMs
        savedPlayWhenReady = player?.playWhenReady ?: savedPlayWhenReady
        resetSeekPreview()
        saveWatchProgress()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SKIP_INTRO_END_MS = "skip_intro_end_ms"
        /** VOD identity for the resume record; absent/blank for live (no record). */
        const val EXTRA_VOD_ID = "vod_id"
        const val EXTRA_VOD_TITLE = "vod_title"
        const val EXTRA_EPISODE_INDEX = "episode_index"
        const val EXTRA_EPISODE_TITLE = "episode_title"
        /** Resume position in ms (already thresholded by WatchRecord); 0 = from start. */
        const val EXTRA_START_POSITION_MS = "start_position_ms"
        /** All playable line URLs of the current channel (round 9). */
        const val EXTRA_LINE_URLS = "line_urls"
        /** Index into [EXTRA_LINE_URLS] of the line being played. */
        const val EXTRA_LINE_INDEX = "line_index"
        private const val TAG = "PlayerActivity"
        private const val CONTROLS_TIMEOUT_MS = 4_000L
        /** Periodic resume-save interval while playing. */
        private const val PROGRESS_SAVE_INTERVAL_MS = 30_000L
        /** Give up on a candidate line after this long; old line survives. */
        private const val LINE_SWITCH_TIMEOUT_MS = 10_000L
    }
}
