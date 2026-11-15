package com.ciweihaha.oriontv.ui.favorites

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ciweihaha.oriontv.R
import com.ciweihaha.oriontv.data.AppSources
import com.ciweihaha.oriontv.data.model.FavoriteItem
import com.ciweihaha.oriontv.databinding.ActivityFavoritesBinding
import com.ciweihaha.oriontv.ui.common.PosterArt
import com.ciweihaha.oriontv.ui.common.PosterLoader
import com.ciweihaha.oriontv.ui.vod.VodDetailActivity
import kotlinx.coroutines.CoroutineScope

/** All favourited titles, newest first. Tapping one opens its detail page. */
class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.favoriteGrid.layoutManager = GridLayoutManager(this, 6)
    }

    override fun onResume() {
        super.onResume()
        val items = AppSources.library(this).favorites()
        binding.favoriteGrid.adapter = FavoriteAdapter(lifecycleScope, items) { item ->
            startActivity(
                Intent(this, VodDetailActivity::class.java)
                    .putExtra(VodDetailActivity.EXTRA_VOD_ID, item.vodId),
            )
        }
        binding.emptyHint.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private class FavoriteAdapter(
        private val scope: CoroutineScope,
        private val items: List<FavoriteItem>,
        private val onClick: (FavoriteItem) -> Unit,
    ) : RecyclerView.Adapter<FavoriteAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val poster: ImageView = view.findViewById(R.id.poster_image)
            val title: TextView = view.findViewById(R.id.poster_title)
            val meta: TextView = view.findViewById(R.id.poster_meta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_poster, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val density = holder.itemView.resources.displayMetrics.density
            // R13-I: favourites used to show only the generated gradient
            // block even though the item carries a poster URL. Same
            // pattern as the browse screen (real poster, placeholder
            // stays as the fallback).
            holder.poster.setImageDrawable(
                PosterArt.make(
                    holder.itemView.context, item.title,
                    (160 * density).toInt(), (240 * density).toInt(),
                ),
            )
            PosterLoader.load(
                holder.poster, item.posterUrl,
                (160 * density).toInt(), (240 * density).toInt(), scope,
            )
            holder.title.text = item.title
            holder.meta.text = listOfNotNull(item.year, item.remarks).joinToString(" · ")
            holder.itemView.isFocusable = true
            holder.itemView.isFocusableInTouchMode = true
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
