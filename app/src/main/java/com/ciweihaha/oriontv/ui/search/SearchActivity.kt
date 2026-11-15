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
import com.ciweihaha.oriontv.databinding.ActivitySearchBinding
import com.ciweihaha.oriontv.ui.common.PosterArt
import com.ciweihaha.oriontv.ui.vod.VodDetailActivity
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
        val config = AppSources.config(this)
        if (!config.isVodConfigured) {
            binding.emptyHint.visibility = View.VISIBLE
            binding.emptyHint.text = "未配置点播源，请先到「设置」添加 TVBox 源地址"
            return
        }
        searching = true
        binding.emptyHint.visibility = View.GONE
        binding.emptyHint.text = "搜索中…"
        lifecycleScope.launch {
            val source = AppSources.source(this@SearchActivity)
            val items = runCatching { source.search(query, 1) }.getOrDefault(emptyList())
            searching = false
            AppSources.library(this@SearchActivity).addSearchQuery(query)
            refreshHistory()
            binding.resultGrid.adapter = PosterAdapter(items) { item ->
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
}
