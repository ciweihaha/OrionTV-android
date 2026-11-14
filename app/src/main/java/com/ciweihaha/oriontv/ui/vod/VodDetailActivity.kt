package com.ciweihaha.oriontv.ui.vod

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ciweihaha.oriontv.R
import com.ciweihaha.oriontv.data.model.Episode
import com.ciweihaha.oriontv.data.source.AggSourceRepository
import com.ciweihaha.oriontv.databinding.ActivityVodDetailBinding
import com.ciweihaha.oriontv.ui.common.PosterArt
import com.ciweihaha.oriontv.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/**
 * VOD detail: poster Hero (left) + title/meta/description (right) +
 * episode grid. Episodes come from the agg CMS source (stage 2) with
 * real play URLs; tapping one (or the play button) opens the player.
 */
class VodDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVodDetailBinding
    private val source = AggSourceRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVodDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.episodeGrid.layoutManager = GridLayoutManager(this, 6)

        val vodId = intent.getStringExtra(EXTRA_VOD_ID) ?: return
        lifecycleScope.launch {
            val detail = source.detail(vodId) ?: return@launch
            val density = resources.displayMetrics.density
            binding.detailPoster.setImageDrawable(
                PosterArt.make(
                    this@VodDetailActivity, detail.item.title,
                    (240 * density).toInt(), (360 * density).toInt(),
                ),
            )
            binding.detailTitle.text = detail.item.title
            binding.detailMeta.text =
                listOfNotNull(detail.item.year, detail.item.remarks).joinToString(" · ")
            binding.detailDescription.text = detail.description
            binding.episodeGrid.adapter = EpisodeAdapter(detail.episodes) { episode ->
                playEpisode(detail.item.title, episode)
            }
            val firstPlayable = detail.episodes.firstOrNull { !it.url.isNullOrBlank() }
            binding.playButton.isEnabled = firstPlayable != null
            binding.playButton.text = if (firstPlayable != null) "播放" else "暂无可播放剧集"
            binding.playButton.setOnClickListener {
                firstPlayable?.let { playEpisode(detail.item.title, it) }
            }
        }
    }

    private fun playEpisode(title: String, episode: Episode) {
        val url = episode.url.orEmpty()
        if (url.isEmpty()) {
            Toast.makeText(this, "该集暂无可播放地址", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, url)
                .putExtra(PlayerActivity.EXTRA_TITLE, "$title · ${episode.title}"),
        )
    }

    private class EpisodeAdapter(
        private val items: List<Episode>,
        private val onClick: (Episode) -> Unit,
    ) : RecyclerView.Adapter<EpisodeAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val button: Button = view.findViewById(R.id.episode_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_episode, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val episode = items[position]
            holder.button.text = episode.title
            holder.button.isFocusable = true
            holder.button.isFocusableInTouchMode = true
            holder.button.setOnClickListener { onClick(episode) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        const val EXTRA_VOD_ID = "vod_id"
    }
}
