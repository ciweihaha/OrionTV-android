package com.ciweihaha.oriontv.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ciweihaha.oriontv.R
import com.ciweihaha.oriontv.data.AppSources
import com.ciweihaha.oriontv.data.model.VodItem
import com.ciweihaha.oriontv.data.source.AggSourceRepository
import com.ciweihaha.oriontv.databinding.ActivitySearchBinding
import com.ciweihaha.oriontv.ui.common.PosterArt
import com.ciweihaha.oriontv.ui.common.PosterLoader
import com.ciweihaha.oriontv.ui.vod.VodDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Search: queries the configured TVBox source (`ac=videolist&wd=...`).
 * Without a VOD source configured the page explains that instead of
 * pretending to search. Recent queries are kept locally and shown as
 * chips; tapping one re-runs it.
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private var searching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.resultGrid.layoutManager = GridLayoutManager(this, 6)
        binding.historyList.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        refreshHistory()

        binding.searchButton.setOnClickListener { runSearch() }
        binding.searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                runSearch()
                true
            } else {
                false
            }
        }
        binding.searchInput.requestFocus()
    }

    private fun refreshHistory() {
        val history = AppSources.library(this).searchHistory()
        binding.historyLabel.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
        binding.historyList.adapter = HistoryAdapter(history) { query ->
            binding.searchInput.setText(query)
            runSearch()
        }
    }

    private fun runSearch() {
        if (searching) return
        val query = binding.searchInput.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "请输入片名", Toast.LENGTH_SHORT).show()
            return
        }
        searching = true
        // R13-H: the "搜索中…" text used to be set while the hint stayed
        // GONE, so a search showed stale results / blank with no loading
        // state at all. Show it now; the completion path hides it again.
        binding.emptyHint.visibility = View.VISIBLE
        binding.emptyHint.text = "搜索中…"
        lifecycleScope.launch {
            // R13-G: search the agg catalogue first — it is the default
            // VOD, so search works out of the box with no TVBox source
            // configured (previously the screen just told the user to
            // configure one). When a TVBox source IS configured, search
            // it too and merge, agg results first, deduped by id.
            val config = AppSources.config(this@SearchActivity)
            val aggDeferred = async {
                runCatching { AggSourceRepository().search(query, 1) }.getOrDefault(emptyList())
            }
            val tvBoxDeferred = async {
                if (!config.isVodConfigured) emptyList()
                else {
                    val source = AppSources.source(this@SearchActivity)
                    runCatching { source.search(query, 1) }.getOrDefault(emptyList())
                }
            }
            val items = (aggDeferred.await() + tvBoxDeferred.await()).distinctBy { it.id }
            searching = false
            AppSources.library(this@SearchActivity).addSearchQuery(query)
            refreshHistory()
            binding.resultGrid.adapter = PosterAdapter(lifecycleScope, items) { item ->
                startActivity(
                    Intent(this@SearchActivity, VodDetailActivity::class.java)
                        .putExtra(VodDetailActivity.EXTRA_VOD_ID, item.id),
                )
            }
            binding.emptyHint.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            if (items.isEmpty()) binding.emptyHint.text = "没有找到「$query」"
        }
    }

    private class HistoryAdapter(
        private val items: List<String>,
        private val onSelect: (String) -> Unit,
    ) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.chip_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chip, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.text.text = items[position]
            holder.itemView.isFocusable = true
            holder.itemView.isFocusableInTouchMode = true
            holder.itemView.setOnClickListener { onSelect(items[position]) }
        }

        override fun getItemCount(): Int = items.size
    }

    private class PosterAdapter(
        private val scope: CoroutineScope,
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
            // R13-I: the browse screen loads real posters; search used
            // to show only the generated gradient block even though the
            // item carries a poster URL. Same pattern as the browser.
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
