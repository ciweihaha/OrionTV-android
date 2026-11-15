package com.ciweihaha.oriontv.ui.vod

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ciweihaha.oriontv.R
import com.ciweihaha.oriontv.data.AppSources
import com.ciweihaha.oriontv.data.model.Episode
import com.ciweihaha.oriontv.data.model.FavoriteItem
import com.ciweihaha.oriontv.data.model.VodDetail
import com.ciweihaha.oriontv.data.settings.AppSettings
import com.ciweihaha.oriontv.data.source.AggSourceRepository
import com.ciweihaha.oriontv.databinding.ActivityVodDetailBinding
import com.ciweihaha.oriontv.ui.common.PosterArt
import com.ciweihaha.oriontv.ui.common.PosterLoader
import com.ciweihaha.oriontv.ui.player.IntroSkip
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
    private var selectedSource: String? = null
    private var currentDetail: VodDetail? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVodDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.episodeGrid.layoutManager = GridLayoutManager(this, 6)
        binding.sourceList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val vodId = intent.getStringExtra(EXTRA_VOD_ID)
        if (vodId.isNullOrBlank()) {
            showDetailError("缺少节目信息，无法打开详情")
            return
        }
        lifecycleScope.launch {
            // Agg CMS first; if it has no such title (e.g. a TVBox search
            // result), fall back to the configured TVBox source (round 1).
            // With no TVBox API configured that call returns null at once.
            val detail = source.detail(vodId)
                ?: runCatching { AppSources.source(this@VodDetailActivity).detail(vodId) }
                    .getOrNull()
            if (detail == null) {
                // Was a silent blank screen: say so, and keep the screen
                // usable (back) instead of showing an empty hero.
                showDetailError("详情加载失败，请检查网络后重试")
                return@launch
            }
            currentDetail = detail
            refreshFavoriteButton()
            binding.favoriteButton.setOnClickListener { toggleFavorite() }
            val density = resources.displayMetrics.density
            val widthPx = (240 * density).toInt()
            val heightPx = (360 * density).toInt()
            binding.detailPoster.setImageDrawable(
                PosterArt.make(this@VodDetailActivity, detail.item.title, widthPx, heightPx),
            )
            PosterLoader.load(
                binding.detailPoster, detail.item.posterUrl,
                widthPx, heightPx, lifecycleScope,
            )
            binding.detailTitle.text = detail.item.title
            binding.detailMeta.text =
                listOfNotNull(detail.item.year, detail.item.remarks).joinToString(" · ")
            binding.detailDescription.text = detail.description
            setupSources(detail)
        }
    }

    /** Detail-load failure: visible error text + toast, play disabled. */
    private fun showDetailError(message: String) {
        binding.detailError.text = message
        binding.detailError.visibility = View.VISIBLE
        binding.playButton.isEnabled = false
        binding.favoriteButton.isEnabled = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /** Round 1: favourite toggle, backed by LibraryStore. */
    private fun refreshFavoriteButton() {
        val detail = currentDetail ?: return
        val isFavorite = AppSources.library(this).isFavorite(detail.item.id)
        binding.favoriteButton.text = if (isFavorite) "已收藏" else "收藏"
    }

    private fun toggleFavorite() {
        val detail = currentDetail ?: return
        val item = detail.item
        val nowFavorite = AppSources.library(this).toggleFavorite(
            FavoriteItem(
                vodId = item.id,
                title = item.title,
                posterUrl = item.posterUrl,
                year = item.year,
                remarks = item.remarks,
            ),
        )
        binding.favoriteButton.text = if (nowFavorite) "已收藏" else "收藏"
        Toast.makeText(
            this,
            if (nowFavorite) "已收藏" else "已取消收藏",
            Toast.LENGTH_SHORT,
        ).show()
    }

    /**
     * M12: episodes carry their source group; with more than one source
     * a chip row lets the user switch which source's episodes (and
     * play button) are shown. One source (or none) needs no row.
     */
    private fun setupSources(detail: VodDetail) {
        val sources = detail.episodes.mapNotNull { it.source }.distinct()
        selectedSource = sources.firstOrNull()
        if (sources.size > 1) {
            binding.sourceList.visibility = View.VISIBLE
            binding.sourceList.adapter = SourceAdapter(sources, selectedSource) { chosen ->
                selectedSource = chosen
                (binding.sourceList.adapter as SourceAdapter).selected = chosen
                binding.sourceList.adapter?.notifyDataSetChanged()
                showEpisodes(detail)
            }
        } else {
            binding.sourceList.visibility = View.GONE
        }
        showEpisodes(detail)
    }

    private fun showEpisodes(detail: VodDetail) {
        val episodes = detail.episodes.filter {
            selectedSource == null || it.source == selectedSource
        }
        binding.episodeGrid.adapter = EpisodeAdapter(episodes) { episode ->
            playEpisode(detail.item.title, episode)
        }
        val firstPlayable = episodes.firstOrNull { !it.url.isNullOrBlank() }
        binding.playButton.isEnabled = firstPlayable != null
        // Resume (round 12): the play button offers to continue when this
        // episode already has a usable breakpoint.
        val resumeMs = firstPlayable?.let {
            AppSources.library(this).progressFor(detail.item.id, it.index)?.resumePositionMs()
        } ?: 0L
        binding.playButton.text = when {
            firstPlayable == null -> "暂无可播放剧集"
            resumeMs > 0 -> "继续播放"
            else -> "播放"
        }
        binding.playButton.setOnClickListener {
            firstPlayable?.let { playEpisode(detail.item.title, it) }
        }
    }

    private fun playEpisode(title: String, episode: Episode) {
        val url = episode.url.orEmpty()
        if (url.isEmpty()) {
            Toast.makeText(this, "该集暂无可播放地址", Toast.LENGTH_SHORT).show()
            return
        }
        val detail = currentDetail ?: return
        // Resume (round 12): reopening an episode continues where the
        // viewer left off; resumePositionMs() already drops too-early
        // (under 10s) and near-end (within 60s) stops.
        val resumeMs = AppSources.library(this)
            .progressFor(detail.item.id, episode.index)
            ?.resumePositionMs() ?: 0L
        // 片头结束点：学到的落点优先（本剧通用），没有学过用设置里的默认时长，
        // 0 表示不跳过（播放器不显示跳过按钮）。
        val learnedMs = AppSources.library(this).learnedIntroEndMs(detail.item.id)
        val introEndMs = IntroSkip.resolveIntroEndMs(
            learnedMs,
            AppSettings(this).getSkipIntroSeconds(),
        )
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, url)
                .putExtra(PlayerActivity.EXTRA_TITLE, "$title · ${episode.title}")
                .putExtra(PlayerActivity.EXTRA_VOD_ID, detail.item.id)
                .putExtra(PlayerActivity.EXTRA_VOD_TITLE, title)
                .putExtra(PlayerActivity.EXTRA_EPISODE_INDEX, episode.index)
                .putExtra(PlayerActivity.EXTRA_EPISODE_TITLE, episode.title)
                .putExtra(PlayerActivity.EXTRA_START_POSITION_MS, resumeMs)
                .putExtra(PlayerActivity.EXTRA_SKIP_INTRO_END_MS, introEndMs),
        )
        if (resumeMs > 0) {
            Toast.makeText(this, "从断点继续播放", Toast.LENGTH_SHORT).show()
        }
    }

    private class SourceAdapter(
        private val items: List<String>,
        var selected: String?,
        private val onSelect: (String) -> Unit,
    ) : RecyclerView.Adapter<SourceAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.chip_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chip, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val name = items[position]
            holder.text.text = name
            holder.itemView.isSelected = name == selected
            holder.itemView.isFocusable = true
            holder.itemView.isFocusableInTouchMode = true
            holder.itemView.setOnClickListener { onSelect(name) }
        }

        override fun getItemCount(): Int = items.size
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
