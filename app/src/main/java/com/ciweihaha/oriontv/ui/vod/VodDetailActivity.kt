package com.ciweihaha.oriontv.ui.vod

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
import com.ciweihaha.oriontv.data.source.PlaceholderSourceRepository
import com.ciweihaha.oriontv.databinding.ActivityVodDetailBinding
import com.ciweihaha.oriontv.ui.common.PosterArt
import kotlinx.coroutines.launch

/**
 * VOD detail: poster Hero (left) + title/meta/description (right) +
 * episode grid. Episodes have no playable URL in stage 1; tapping one
 * says so instead of pretending to play.
 */
class VodDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVodDetailBinding
    private val source = PlaceholderSourceRepository()

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
            binding.episodeGrid.adapter = EpisodeAdapter(detail.episodes) {
                Toast.makeText(
                    this@VodDetailActivity,
                    "待接入真实源（第二阶段）",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            binding.playButton.isEnabled = false
            binding.playButton.text = "待接入真实源（第二阶段）"
        }
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
