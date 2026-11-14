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
import com.ciweihaha.oriontv.data.epg.PlaceholderEpgRepository
import com.ciweihaha.oriontv.data.model.Channel
import com.ciweihaha.oriontv.data.model.Program
import com.ciweihaha.oriontv.data.source.PlaceholderSourceRepository
import com.ciweihaha.oriontv.databinding.ActivityLiveBinding
import com.ciweihaha.oriontv.ui.player.PlayerActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live TV: channel list (left) + now/next detail (right).
 * Stage 1 shows placeholder channels; the play button stays disabled
 * until a real source provides a stream URL (stage 2).
 */
class LiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveBinding
    private val source = PlaceholderSourceRepository()
    private val epg = PlaceholderEpgRepository()
    private var channels: List<Channel> = emptyList()
    private var selected: Channel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.channelList.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            channels = source.channels()
            binding.channelList.adapter = ChannelAdapter(channels) { channel ->
                select(channel)
            }
            val initialId = intent.getStringExtra(EXTRA_CHANNEL_ID)
            select(channels.find { it.id == initialId } ?: channels.firstOrNull())
        }

        binding.playButton.setOnClickListener {
            val channel = selected ?: return@setOnClickListener
            val url = channel.streamUrl ?: return@setOnClickListener
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, url)
                    .putExtra(PlayerActivity.EXTRA_TITLE, channel.name),
            )
        }
    }

    private fun select(channel: Channel?) {
        selected = channel
        if (channel == null) return
        binding.detailName.text = channel.name
        binding.detailGroup.text = channel.group
        binding.playButton.isEnabled = channel.streamUrl != null
        binding.playButton.text =
            if (channel.streamUrl != null) "播放" else "待接入真实源（第二阶段）"
        lifecycleScope.launch {
            val (now, next) = epg.nowAndNext(channel.id)
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
