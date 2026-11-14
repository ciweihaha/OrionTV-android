package com.ciweihaha.oriontv.data.source

import com.ciweihaha.oriontv.data.model.VodCategory
import com.ciweihaha.oriontv.data.model.VodDetail
import com.ciweihaha.oriontv.data.model.VodItem
import com.ciweihaha.oriontv.data.model.Channel

/**
 * VOD from agg (Apple CMS), live channels still from the placeholder
 * until the M3U source is wired (stage 2).
 *
 * Categories come from the `class` array of `videolist(t=1, pg=1)`:
 * 1 电影 / 2 电视剧 / 3 动漫 / 4 综艺 / 108 儿童启蒙, with "全部"
 * prepended. "全部" maps to `t=1` (the default first page, same as the
 * reference home). Failures are fail-soft: empty list / null, never a
 * throw — the UI shows its empty state instead of crashing.
 */
class AggSourceRepository(
    private val client: AggCmsClient = AggCmsClient(),
    private val channelSource: SourceRepository = PlaceholderSourceRepository(),
) : SourceRepository {

    private var cachedCategories: List<VodCategory>? = null

    override suspend fun channels(): List<Channel> =
        runCatching { channelSource.channels() }.getOrDefault(emptyList())

    override suspend fun categories(): List<VodCategory> {
        cachedCategories?.let { return it }
        val result = runCatching {
            val first = client.videolist(1, 1)
            val list = mutableListOf(VodCategory("all", "全部"))
            first.classes.forEach { list += VodCategory(it.typeId.toString(), it.typeName) }
            list.toList()
        }.getOrDefault(emptyList())
        if (result.isNotEmpty()) cachedCategories = result
        return result
    }

    override suspend fun browse(categoryId: String, page: Int): List<VodItem> =
        runCatching {
            val type = categoryId.toIntOrNull() ?: 1
            client.videolist(type, page).list.map { it.toVodItem() }
        }.getOrDefault(emptyList())

    override suspend fun detail(vodId: String): VodDetail? =
        runCatching {
            val item = client.detail(vodId) ?: return@runCatching null
            VodDetail(
                item = item.toVodItem(),
                description = item.content?.takeIf { it.isNotBlank() } ?: item.blurb,
                episodes = client.parseEpisodes(item.playFrom, item.playUrl),
            )
        }.getOrNull()

    private fun AggCmsClient.VideoItem.toVodItem(): VodItem = VodItem(
        id = vodId,
        title = name,
        posterUrl = pic,
        year = year,
        remarks = remarks,
    )
}
