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
import com.ciweihaha.oriontv.data.epg.PlaceholderEpgRepository
import com.ciweihaha.oriontv.data.model.Channel
import com.ciweihaha.oriontv.data.model.HomeAction
import com.ciweihaha.oriontv.data.model.VodCategory
import com.ciweihaha.oriontv.data.placeholder.PlaceholderData
import com.ciweihaha.oriontv.data.source.AggSourceRepository
import com.ciweihaha.oriontv.data.update.UpdateChecker
import com.ciweihaha.oriontv.ui.live.LiveActivity
import com.ciweihaha.oriontv.ui.player.PlayerActivity
import com.ciweihaha.oriontv.ui.settings.SettingsActivity
import com.ciweihaha.oriontv.ui.vod.VodBrowseActivity
import kotlinx.coroutines.launch

/**
 * Home browse screen (Leanback). Rows: 继续观看 / 直播 / 点播 / 应用.
 * Type scale is one step down from stock Leanback (T9); gold is the
 * brand/focus colour. Live rows are still stage-1 placeholder data;
 * VOD categories now come from the agg CMS source.
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
                is Channel -> startActivity(
                    Intent(requireContext(), LiveActivity::class.java)
                        .putExtra(LiveActivity.EXTRA_CHANNEL_ID, item.id),
                )
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
                }
            }
        }

        loadRows()
    }

    private fun loadRows() {
        viewLifecycleOwner.lifecycleScope.launch {
            val channels = source.channels()
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
