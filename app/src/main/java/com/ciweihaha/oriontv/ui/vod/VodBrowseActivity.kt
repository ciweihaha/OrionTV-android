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
import com.ciweihaha.oriontv.data.source.AggSourceRepository
import com.ciweihaha.oriontv.databinding.ActivityVodBrowseBinding
import com.ciweihaha.oriontv.ui.common.PosterArt
import kotlinx.coroutines.launch

/**
 * VOD browse: category chips on top, poster wall below.
 * Categories and items come from the agg CMS source (stage 2); the
 * repository fails soft to an empty list, shown via [emptyHint].
 * Detail opens the Hero page.
 *
 * Pagination (round 9): the poster wall loads page 1, then keeps
 * appending pages as the user scrolls near the end, until the CMS
 * reports the last page. A failed page load keeps the previous state,
 * so scrolling can retry it; switching category resets to page 1.
 */
class VodBrowseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVodBrowseBinding
    private val source = AggSourceRepository()
    private var selectedCategory = "all"
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var posterAdapter: PosterAdapter
    private lateinit var gridLayoutManager: GridLayoutManager
    private val paging = PagingState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVodBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.categoryList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        gridLayoutManager = GridLayoutManager(this, 6)
        binding.posterGrid.layoutManager = gridLayoutManager
        posterAdapter = PosterAdapter { item ->
            startActivity(
                Intent(this@VodBrowseActivity, VodDetailActivity::class.java)
                    .putExtra(VodDetailActivity.EXTRA_VOD_ID, item.id),
            )
        }
        binding.posterGrid.adapter = posterAdapter
        binding.posterGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lastVisible = gridLayoutManager.findLastVisibleItemPosition()
                if (paging.shouldLoadMore(lastVisible, posterAdapter.itemCount)) {
                    loadPage(paging.nextPage)
                }
            }
        })

        lifecycleScope.launch {
            val categories = source.categories()
            selectedCategory = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: "all"
            categoryAdapter = CategoryAdapter(categories, selectedCategory) { category ->
                selectedCategory = category.id
                categoryAdapter.selectedId = category.id
                categoryAdapter.notifyDataSetChanged()
                paging.reset()
                posterAdapter.setItems(emptyList())
                binding.emptyHint.visibility = View.GONE
                loadPage(1)
            }
            binding.categoryList.adapter = categoryAdapter
            loadPage(1)
        }
    }

    private fun loadPage(page: Int) {
        if (!paging.canLoadMore()) return
        paging.onLoadStarted()
        updateLoadingMore()
        lifecycleScope.launch {
            val result = source.browsePaged(selectedCategory, page)
            if (result.items.isEmpty() && page > 1) {
                // An empty follow-up page is a failed load, not the end of
                // the list: keep the previous page/hasMore so scrolling can
                // retry it instead of stopping pagination for good.
                paging.onLoadFailed()
            } else {
                paging.onPageLoaded(page, result.hasMore)
                if (page == 1) {
                    posterAdapter.setItems(result.items)
                } else {
                    posterAdapter.appendItems(result.items)
                }
                binding.emptyHint.visibility =
                    if (posterAdapter.itemCount == 0) View.VISIBLE else View.GONE
            }
            updateLoadingMore()
        }
    }

    private fun updateLoadingMore() {
        binding.loadingMore.visibility =
            if (paging.isLoading && paging.currentPage > 0) View.VISIBLE else View.GONE
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
        private val onClick: (VodItem) -> Unit,
    ) : RecyclerView.Adapter<PosterAdapter.Holder>() {

        private val items = mutableListOf<VodItem>()

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val poster: ImageView = view.findViewById(R.id.poster_image)
            val title: TextView = view.findViewById(R.id.poster_title)
            val meta: TextView = view.findViewById(R.id.poster_meta)
        }

        fun setItems(newItems: List<VodItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun appendItems(newItems: List<VodItem>) {
            if (newItems.isEmpty()) return
            val start = items.size
            items.addAll(newItems)
            notifyItemRangeInserted(start, newItems.size)
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
