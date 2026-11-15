package com.ciweihaha.oriontv.ui.home

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.lifecycleScope
import com.ciweihaha.oriontv.R
import com.ciweihaha.oriontv.data.AppSources
import com.ciweihaha.oriontv.data.epg.PlaceholderEpgRepository
import com.ciweihaha.oriontv.data.model.Channel
import com.ciweihaha.oriontv.data.model.HomeAction
import com.ciweihaha.oriontv.data.model.VodCategory
import com.ciweihaha.oriontv.data.placeholder.PlaceholderData
import com.ciweihaha.oriontv.data.source.AggSourceRepository
import com.ciweihaha.oriontv.data.update.UpdateChecker
import com.ciweihaha.oriontv.ui.favorites.FavoritesActivity
import com.ciweihaha.oriontv.ui.live.LiveActivity
import com.ciweihaha.oriontv.ui.player.PlayerActivity
import com.ciweihaha.oriontv.ui.search.SearchActivity
import com.ciweihaha.oriontv.ui.settings.SettingsActivity
import com.ciweihaha.oriontv.ui.vod.VodBrowseActivity
import kotlinx.coroutines.launch

/**
 * Home browse screen (Leanback). Rows: 继续观看 / 直播 / 点播 / 应用.
 * Type scale is one step down from stock Leanback (T9); gold is the
 * brand/focus colour. The live row shows the real M3U channels
 * (default: the user's own live.m3u, same playlist LiveActivity
 * loads); VOD categories come from the agg CMS source. When the live
 * playlist cannot be loaded the row falls back to the placeholder
 * demo set so the home screen still renders instead of going blank.
 */
@Suppress("DEPRECATION")
class HomeFragment : BrowseSupportFragment() {

    private val source = AggSourceRepository()
    private val epg = PlaceholderEpgRepository()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = Color.parseColor("#E8C15A")
        searchAffordanceColor = Color.parseColor("#12141F")

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Channel -> {
                    val intent = Intent(requireContext(), LiveActivity::class.java)
                    // M8: placeholder (demo) channels have no stream URL
                    // and their ids never match a real M3U channel, so
                    // passing the id would silently open the first
                    // channel no matter which row was tapped. Only pass
                    // the id for a real channel; a placeholder opens the
                    // live list, which starts at its first real channel.
                    if (!item.streamUrl.isNullOrBlank()) {
                        intent.putExtra(LiveActivity.EXTRA_CHANNEL_ID, item.id)
                    }
                    startActivity(intent)
                }
                is VodCategory -> startActivity(
                    Intent(requireContext(), VodBrowseActivity::class.java)
                        .putExtra(VodBrowseActivity.EXTRA_CATEGORY_ID, item.id),
                )
                is HomeAction -> when (item.id) {
                    "test_stream" -> startActivity(
                        Intent(requireContext(), PlayerActivity::class.java)
                            .putExtra(PlayerActivity.EXTRA_URL, PlaceholderData.TEST_STREAM_URL)
                            .putExtra(PlayerActivity.EXTRA_TITLE, PlaceholderData.TEST_STREAM_TITLE)
                            // Demo intro window so the skip-intro button shows.
                            .putExtra(PlayerActivity.EXTRA_SKIP_INTRO_END_MS, 30_000L),
                    )
                    "m3u_settings" -> startActivity(
                        Intent(requireContext(), SettingsActivity::class.java),
                    )
                    "check_update" -> checkForUpdate()
                    // Round 1: search + favorites entry points.
                    "search" -> startActivity(
                        Intent(requireContext(), SearchActivity::class.java),
                    )
                    "favorites" -> startActivity(
                        Intent(requireContext(), FavoritesActivity::class.java),
                    )
                }
            }
        }

        loadRows()
    }

    private fun loadRows() {
        // Resolve the live source here (context is valid in
        // onActivityCreated) rather than inside the coroutine, so a
        // destroyed view can never make requireContext() throw.
        val liveSource = AppSources.source(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            // Real live channels (M3U). Both loaders are fail-soft
            // and never throw: an empty real list means the playlist
            // could not be fetched, so fall back to the placeholder
            // demo set — the same fallback LiveActivity uses —
            // instead of leaving the row empty.
            val realChannels = liveSource.channels()
            val channels = realChannels.ifEmpty { source.channels() }
            val categories = source.categories()
            val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

            val continueAdapter = ArrayObjectAdapter(TextCardPresenter())
            continueAdapter.add("暂无观看记录，去「点播」看看吧")
            rowsAdapter.add(ListRow(HeaderItem(0, "继续观看"), continueAdapter))

            val liveAdapter = ArrayObjectAdapter(PosterCardPresenter())
            channels.forEach { liveAdapter.add(it) }
            rowsAdapter.add(ListRow(HeaderItem(1, "直播"), liveAdapter))

            val vodAdapter = ArrayObjectAdapter(TextCardPresenter())
            categories.forEach { vodAdapter.add(it) }
            rowsAdapter.add(ListRow(HeaderItem(2, "点播"), vodAdapter))

            val appAdapter = ArrayObjectAdapter(PosterCardPresenter())
            appAdapter.add(HomeAction("search", "搜索", "按片名搜索点播"))
            appAdapter.add(HomeAction("favorites", "我的收藏", "收藏的影片"))
            appAdapter.add(HomeAction("test_stream", "播放器测试", "BipBop 测试流 · 验证 Media3"))
            appAdapter.add(HomeAction("m3u_settings", "直播源设置", "配置 M3U 直播地址"))
            appAdapter.add(HomeAction("check_update", "检查更新", "走 okys-agg 版本接口"))
            rowsAdapter.add(ListRow(HeaderItem(3, "应用"), appAdapter))

            adapter = rowsAdapter
        }
    }

    private fun checkForUpdate() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = UpdateChecker().check()
            val message = result.fold(
                onSuccess = { r ->
                    if (r.updateAvailable) {
                        "发现新版本 ${r.latestVersion}（当前 ${com.ciweihaha.oriontv.BuildConfig.VERSION_NAME}）"
                    } else {
                        "已是最新版本 ${r.latestVersion}"
                    }
                },
                onFailure = { e -> "检查失败：${e.message}" },
            )
            AlertDialog.Builder(requireContext())
                .setTitle("检查更新")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show()
        }
    }
}
