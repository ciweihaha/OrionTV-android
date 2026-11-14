package com.ciweihaha.oriontv.ui.vod

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ciweihaha.oriontv.R
import com.ciweihaha.oriontv.data.model.VodCategory
import com.ciweihaha.oriontv.data.model.VodItem
import com.ciweihaha.oriontv.data.source.PlaceholderSourceRepository
import com.ciweihaha.oriontv.databinding.ActivityVodBrowseBinding
import com.ciweihaha.oriontv.ui.common.PosterArt
import kotlinx.coroutines.launch

/**
 * VOD browse: category chips on top, poster wall below.
 * Stage 1 shows placeholder items; detail opens the Hero page.
 */
class VodBrowseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVodBrowseBinding
    private val source = PlaceholderSourceRepository()
    private var selectedCategory = "all"
    private lateinit var categoryAdapter: CategoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVodBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.categoryList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.posterGrid.layoutManager = GridLayoutManager(this, 6)

        lifecycleScope.launch {
            val categories = source.categories()
            selectedCategory = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: "all"
            categoryAdapter = CategoryAdapter(categories, selectedCategory) { category ->
                selectedCategory = category.id
                categoryAdapter.selectedId = category.id
                categoryAdapter.notifyDataSetChanged()
                loadItems()
            }
            binding.categoryList.adapter = categoryAdapter
            loadItems()
        }
    }

    private fun loadItems() {
        lifecycleScope.launch {
            val items = source.browse(selectedCategory, 1)
            binding.posterGrid.adapter = PosterAdapter(items) { item ->
                startActivity(
                    Intent(this@VodBrowseActivity, VodDetailActivity::class.java)
                        .putExtra(VodDetailActivity.EXTRA_VOD_ID, item.id),
                )
            }
            binding.emptyHint.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private class CategoryAdapter(
        private val items: List<VodCategory>,
        var selectedId: String,
        private val onSelect: (VodCategory) -> Unit,
    ) : RecyclerView.Adapter<CategoryAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.chip_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chip, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val category = items[position]
            holder.text.text = category.name
            holder.itemView.isSelected = category.id == selectedId
            holder.itemView.isFocusable = true
            holder.itemView.isFocusableInTouchMode = true
            holder.itemView.setOnClickListener { onSelect(category) }
        }

        override fun getItemCount(): Int = items.size
    }

    private class PosterAdapter(
        private val items: List<VodItem>,
        private val onClick: (VodItem) -> Unit,
    ) : RecyclerView.Adapter<PosterAdapter.Holder>() {

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
            holder.poster.setImageDrawable(
                PosterArt.make(
                    holder.itemView.context, item.title,
                    (160 * density).toInt(), (240 * density).toInt(),
                ),
            )
            holder.title.text = item.title
            holder.meta.text = listOfNotNull(item.year, item.remarks).joinToString(" · ")
            holder.itemView.isFocusable = true
            holder.itemView.isFocusableInTouchMode = true
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
    }
}
