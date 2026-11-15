package com.ciweihaha.oriontv.data.source

import com.ciweihaha.oriontv.data.model.VodCategory
import com.ciweihaha.oriontv.data.model.VodDetail
import com.ciweihaha.oriontv.data.model.VodItem
import com.ciweihaha.oriontv.data.model.VodPage
import com.ciweihaha.oriontv.data.model.Channel
import kotlinx.coroutines.CancellationException

/**
 * VOD from agg (Apple CMS); live channels come from the M3U source when
 * configured (LiveActivity) and fall back to the placeholder demo set
 * here only when no M3U address is configured or the load fails.
 *
 * Categories come from the `class` array of `videolist(t=1, pg=1)`:
 * 1 电影 / 2 电视剧 / 3 动漫 / 4 综艺 / 108 儿童启蒙, with "全部"
 * prepended. "全部" is NOT a type filter (M7 — it used to map to `t=1`,
 * so "全部" only ever returned movies): it fetches every known type
 * for the page and merges them. Failures are fail-soft: empty list /
 * null, never a throw — the UI shows its empty state instead of
 * crashing. Cancellation is never swallowed: it is rethrown so a
 * destroyed screen stops instead of silently returning empty data.
 *
 * Pagination (round 9): [browsePaged] reports `hasMore` from the CMS
 * `pagecount`, so the browse screen can keep loading pages until the
 * last one instead of stopping at page 1.
 */
class AggSourceRepository(
    private val client: AggCmsClient = AggCmsClient(),
    private val channelSource: SourceRepository = PlaceholderSourceRepository(),
) : SourceRepository {

    private var cachedCategories: List<VodCategory>? = null

    override suspend fun channels(): List<Channel> =
        failSoft(emptyList()) { channelSource.channels() }

    override suspend fun categories(): List<VodCategory> {
        cachedCategories?.let { return it }
        val result = failSoft(emptyList()) {
            val first = client.videolist(1, 1)
            val list = mutableListOf(VodCategory("all", "全部"))
            first.classes.forEach { list += VodCategory(it.typeId.toString(), it.typeName) }
            list.toList()
        }
        if (result.isNotEmpty()) cachedCategories = result
        return result
    }

    override suspend fun browse(categoryId: String, page: Int): List<VodItem> =
        browsePaged(categoryId, page).items

    override suspend fun browsePaged(categoryId: String, page: Int): VodPage =
        failSoft(VodPage(emptyList(), hasMore = false)) {
            val type = categoryId.toIntOrNull()
            if (type == null) {
                browseAll(page)
            } else {
                val result = client.videolist(type, page.coerceAtLeast(1))
                VodPage(
                    items = result.list.map { it.toVodItem() },
                    hasMore = result.page < result.pageCount,
                )
            }
        }

    /**
     * M7: "全部" (or any non-numeric category id) fetches page [page] of
     * every known type and merges them, instead of pretending "全部" is
     * t=1 (movies only). `hasMore` is true while any type has another
     * page. When the type list itself cannot be loaded, fall back to
     * the old single-type (t=1) fetch rather than returning nothing.
     */
    private suspend fun browseAll(page: Int): VodPage {
        val types = categories().mapNotNull { it.id.toIntOrNull() }
        if (types.isEmpty()) {
            val result = client.videolist(1, page.coerceAtLeast(1))
            return VodPage(
                items = result.list.map { it.toVodItem() },
                hasMore = result.page < result.pageCount,
            )
        }
        val items = mutableListOf<VodItem>()
        var hasMore = false
        for (type in types) {
            val result = client.videolist(type, page.coerceAtLeast(1))
            items += result.list.map { it.toVodItem() }
            if (result.page < result.pageCount) hasMore = true
        }
        return VodPage(items, hasMore)
    }

    override suspend fun detail(vodId: String): VodDetail? = try {
        val item = client.detail(vodId) ?: return null
        VodDetail(
            item = item.toVodItem(),
            description = item.content?.takeIf { it.isNotBlank() } ?: item.blurb,
            episodes = client.parseEpisodes(item.playFrom, item.playUrl),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** R13-G: search the agg catalogue (the default VOD). Fail-soft
     *  like everything else here — an empty list, never a throw. */
    override suspend fun search(query: String, page: Int): List<VodItem> =
        failSoft(emptyList()) {
            client.search(query, page.coerceAtLeast(1)).list.map { it.toVodItem() }
        }

    /** Fail-soft helper that never swallows coroutine cancellation. */
    private suspend inline fun <T> failSoft(default: T, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            default
        }

    private fun AggCmsClient.VideoItem.toVodItem(): VodItem = VodItem(
        id = vodId,
        title = name,
        posterUrl = pic,
        year = year,
        remarks = remarks,
    )
}
