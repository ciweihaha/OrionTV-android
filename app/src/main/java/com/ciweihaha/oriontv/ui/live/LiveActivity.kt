package com.ciweihaha.oriontv.ui.live

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ciweihaha.oriontv.R
import com.ciweihaha.oriontv.data.AppSources
import com.ciweihaha.oriontv.data.SourceConfig
import com.ciweihaha.oriontv.data.model.Channel
import com.ciweihaha.oriontv.data.model.Program
import com.ciweihaha.oriontv.data.settings.AppSettings
import com.ciweihaha.oriontv.data.source.LiveLines
import com.ciweihaha.oriontv.data.source.M3uDiskCache
import com.ciweihaha.oriontv.data.source.M3uSource
import com.ciweihaha.oriontv.data.source.PlaceholderSourceRepository
import com.ciweihaha.oriontv.databinding.ActivityLiveBinding
import com.ciweihaha.oriontv.ui.player.PlayerActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live TV: channel list (left) + now/next detail (right).
 *
 * Channels come from the configured M3U source (settings): cached list
 * renders instantly, then a refresh updates it. With no M3U address
 * configured (or when both cache and network are empty) the stage-1
 * placeholder channels are shown instead. EPG is the real XMLTV feed
 * (round 1); it shows "—" when the feed has no programme for a channel.
 */
class LiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveBinding
    private val source = PlaceholderSourceRepository()
    /** Round 1: real XMLTV EPG (https://ysp.ciweihaha.win/epg.xml). */
    private val epg by lazy { AppSources.epg(this) }
    private val settings by lazy { AppSettings(this) }
    private val m3uSource by lazy {
        M3uSource(diskCache = M3uDiskCache(File(cacheDir, "m3u")))
    }
    private var channels: List<Channel> = emptyList()
    private var selected: Channel? = null
    /** R13-E: in-flight EPG lookup for the detail panel. Fast scrolling
     *  used to launch one lookup per focus change with no cancellation,
     *  so a slow lookup for channel A could land after the user had
     *  moved to channel B and show A's programmes under B's name. */
    private var epgJob: Job? = null
    /** True when the list on screen is the placeholder set shown only
     *  because the configured M3U failed to load (button copy must say
     *  so instead of implying a real source is coming). */
    private var m3uFailedFallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.channelList.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            // No M3U saved in Settings yet: fall back to the built-in
            // default (the user's own live.m3u) instead of demo
            // channels, so the app plays straight after install with
            // no setup. This is the same address HomeFragment's live
            // row loads (see AppSources.source), so a channel id
            // passed from home always matches this list.
            val m3uUrl = settings.getM3uUrl().takeIf { !it.isNullOrBlank() }
                ?: SourceConfig.DEFAULT_LIVE_M3U
            val cached = m3uSource.getCached(m3uUrl)
            if (cached.isNotEmpty()) {
                m3uFailedFallback = false
                binding.sourceNote.visibility = View.GONE
                showChannels(cached)
            }
            val fresh = m3uSource.refresh(m3uUrl)
            if (fresh.isNotEmpty()) {
                m3uFailedFallback = false
                binding.sourceNote.visibility = View.GONE
                showChannels(fresh)
            } else if (cached.isEmpty()) {
                m3uFailedFallback = true
                binding.sourceNote.text = getString(R.string.m3u_load_failed)
                binding.sourceNote.visibility = View.VISIBLE
                showChannels(source.channels())
            }
        }

        binding.playButton.setOnClickListener {
            val channel = selected ?: return@setOnClickListener
            val url = channel.streamUrl ?: return@setOnClickListener
            // Multi-line (round 9): same-name entries in the playlist are
            // the channel's alternate lines; hand them all to the player
            // so it can switch without leaving playback.
            val lines = LiveLines.linesFor(channels, channel)
            // indexOf returns -1 when the channel is not among its own
            // lines; 0 (the first line) is the explicit fallback here.
            val lineIndex = LiveLines.indexOf(lines, channel).takeIf { it >= 0 } ?: 0
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, url)
                    .putExtra(PlayerActivity.EXTRA_TITLE, channel.name)
                    .putStringArrayListExtra(
                        PlayerActivity.EXTRA_LINE_URLS,
                        ArrayList(lines.mapNotNull { it.streamUrl }),
                    )
                    .putExtra(PlayerActivity.EXTRA_LINE_INDEX, lineIndex),
            )
        }
    }

    private fun showChannels(list: List<Channel>) {
        channels = list
        binding.channelList.adapter = ChannelAdapter(list) { channel ->
            select(channel)
        }
        // M8: only an id that actually exists in this list selects a
        // channel. A stale / placeholder id falls through to the
        // previously selected channel, then the first real one — the
        // caller (HomeFragment) no longer passes placeholder ids at all.
        val initialId = intent.getStringExtra(EXTRA_CHANNEL_ID)
        val target = list.find { it.id == initialId }
            ?: list.find { it.id == selected?.id }
            ?: list.firstOrNull()
        select(target)
        // Rebuilding the adapter drops focus: restore it to the selected
        // row so a refresh does not leave the list unfocused.
        val position = list.indexOf(target)
        if (position >= 0) {
            binding.channelList.post {
                // D4: the target row may be off-screen after a refresh,
                // and findViewHolderForAdapterPosition returns null for
                // rows that were never laid out — focus was silently
                // lost and DPAD input went nowhere. Scroll it into
                // view first, then focus it on the next layout pass.
                binding.channelList.scrollToPosition(position)
                binding.channelList.post {
                    binding.channelList
                        .findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
                }
            }
        }
    }

    private fun select(channel: Channel?) {
        selected = channel
        if (channel == null) return
        binding.detailName.text = channel.name
        binding.detailGroup.text = channel.group
        binding.playButton.isEnabled = channel.streamUrl != null
        binding.playButton.text = when {
            channel.streamUrl != null -> "播放"
            // The old copy ("待接入真实源（第二阶段）") implied a real
            // source was on its way; say what actually happened.
            m3uFailedFallback -> "直播源加载失败，请检查 M3U 地址"
            else -> "演示频道 · 暂无播放地址"
        }
        epgJob?.cancel()
        epgJob = lifecycleScope.launch {
            val (now, next) = epg.nowAndNext(channel.id)
            // R13-E: the user may have moved on while the lookup ran —
            // never paint another channel's programmes under this one.
            if (selected?.id != channel.id) return@launch
            binding.nowTitle.text = now?.let { "正在播：${it.title}" } ?: "正在播：—"
            binding.nowTime.text = now?.let { formatRange(it) } ?: ""
            binding.nowProgress.progress = now?.let { (it.progress() * 100).toInt() } ?: 0
            binding.nextTitle.text = next?.let { "接下来：${it.title}" } ?: "接下来：—"
            binding.nextTime.text = next?.let { formatRange(it) } ?: ""
        }
    }

    private fun formatRange(program: Program): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return "${fmt.format(Date(program.startMs))} - ${fmt.format(Date(program.endMs))}"
    }

    private class ChannelAdapter(
        private val items: List<Channel>,
        private val onFocus: (Channel) -> Unit,
    ) : RecyclerView.Adapter<ChannelAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val number: TextView = view.findViewById(R.id.channel_number)
            val name: TextView = view.findViewById(R.id.channel_name)
            val group: TextView = view.findViewById(R.id.channel_group)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val channel = items[position]
            holder.number.text = channel.number
            holder.name.text = channel.name
            holder.group.text = channel.group
            holder.itemView.isFocusable = true
            holder.itemView.isFocusableInTouchMode = true
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) onFocus(channel)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channel_id"
    }
}
