package com.ciweihaha.oriontv.data.source

import com.ciweihaha.oriontv.data.model.Channel
import com.ciweihaha.oriontv.data.model.Episode
import com.ciweihaha.oriontv.data.model.VodCategory
import com.ciweihaha.oriontv.data.model.VodDetail
import com.ciweihaha.oriontv.data.model.VodItem
import com.ciweihaha.oriontv.data.model.VodPage
import com.ciweihaha.oriontv.data.placeholder.PlaceholderData

/**
 * Source access. VOD is served by [AggSourceRepository] (agg CMS,
 * stage 2); live channels come from the M3U source when configured
 * ([M3uSource], wired in LiveActivity) and fall back to
 * [PlaceholderSourceRepository] demo channels when no M3U address is
 * configured or the load fails. The UI only ever talks to this
 * interface, so the swap touches no screen logic.
 *
 * Red lines (never implement here): 点播首页、T2、BBC、天光云影、TV 5.9.0、凤凰、
 * moontv.
 */
interface SourceRepository {
    suspend fun channels(): List<Channel>
    suspend fun categories(): List<VodCategory>
    suspend fun browse(categoryId: String, page: Int): List<VodItem>
    suspend fun detail(vodId: String): VodDetail?

    /**
     * Search by keyword (round 1). The default implementation returns an
     * empty list, so repositories without a search endpoint (agg CMS)
     * keep working unchanged; [TvBoxSourceRepository] overrides it with
     * the CMS `ac=videolist&wd=` endpoint.
     */
    suspend fun search(query: String, page: Int): List<VodItem> = emptyList()

    /**
     * Paged browse (round 9). The default implementation delegates to
     * [browse] and reports no further pages, so repositories that do not
     * know the total page count keep working unchanged.
     */
    suspend fun browsePaged(categoryId: String, page: Int): VodPage =
        VodPage(browse(categoryId, page), hasMore = false)
}

/** Stage-1 placeholder. Returns the clearly-labelled demo data set. */
class PlaceholderSourceRepository : SourceRepository {
    override suspend fun channels(): List<Channel> = PlaceholderData.channels
    override suspend fun categories(): List<VodCategory> = PlaceholderData.categories
    override suspend fun browse(categoryId: String, page: Int): List<VodItem> =
        PlaceholderData.vodItems(categoryId)

    override suspend fun detail(vodId: String): VodDetail? =
        PlaceholderData.vodItems("all").find { it.id == vodId }?.let { item ->
            VodDetail(
                item = item,
                description = "简介待真实源接入后提供（第二阶段）。",
                episodes = (1..12).map { Episode(it, "第 $it 集") },
            )
        }
}
