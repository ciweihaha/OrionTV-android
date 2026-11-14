package com.ciweihaha.oriontv.data.source

import com.ciweihaha.oriontv.data.model.Channel
import com.ciweihaha.oriontv.data.model.Episode
import com.ciweihaha.oriontv.data.model.VodCategory
import com.ciweihaha.oriontv.data.model.VodDetail
import com.ciweihaha.oriontv.data.model.VodItem
import com.ciweihaha.oriontv.data.placeholder.PlaceholderData

/**
 * Source access. VOD is served by [AggSourceRepository] (agg CMS,
 * stage 2); live channels still come from [PlaceholderSourceRepository]
 * until the M3U source is wired. The UI only ever talks to this
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
