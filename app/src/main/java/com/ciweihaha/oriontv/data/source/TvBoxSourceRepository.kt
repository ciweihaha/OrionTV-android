package com.ciweihaha.oriontv.data.source

import com.ciweihaha.oriontv.data.model.Channel
import com.ciweihaha.oriontv.data.model.VodCategory
import com.ciweihaha.oriontv.data.model.VodDetail
import com.ciweihaha.oriontv.data.model.VodItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Stage-2 source repository: real data, no placeholders.
 *
 * - Live channels come from the configured M3U playlist
 *   (default: the user's own https://live.ciweihaha.win/live.m3u).
 * - VOD comes from a TVBox-compatible CMS JSON API
 *   (`?ac=list` / `?ac=videolist` / `?ac=detail`), configured in Settings.
 *   With no VOD API configured every VOD call returns an empty list and
 *   the UI shows the "not configured" empty state instead of demo data.
 *
 * Network runs on Dispatchers.IO; [fetcher] is injectable so tests never
 * touch the network. Short in-memory caches keep channel flips and
 * repeated browse calls from re-downloading the whole playlist.
 *
 * Red lines (never implement here): 点播首页、T2、BBC、天光云影、TV 5.9.0、凤凰、
 * moontv.
 */
class TvBoxSourceRepository(
    private val fetcher: (String) -> String = HttpFetcher::get,
    private val liveM3uUrl: String,
    private val vodApiUrl: String,
) : SourceRepository {

    private var cachedChannels: List<Channel>? = null
    private var channelsCachedAtMs: Long = 0L
    private var cachedCategories: List<VodCategory>? = null
    private var categoriesCachedAtMs: Long = 0L

    override suspend fun channels(): List<Channel> = withContext(Dispatchers.IO) {
        val cached = cachedChannels
        if (cached != null && System.currentTimeMillis() - channelsCachedAtMs < CHANNELS_TTL_MS) {
            return@withContext cached
        }
        if (liveM3uUrl.isBlank()) return@withContext emptyList()
        val parsed = runCatching { M3uParser.parse(fetcher(liveM3uUrl)) }
            .getOrElse { return@withContext cached.orEmpty() }
        cachedChannels = parsed
        channelsCachedAtMs = System.currentTimeMillis()
        parsed
    }

    override suspend fun categories(): List<VodCategory> = withContext(Dispatchers.IO) {
        val cached = cachedCategories
        if (cached != null && System.currentTimeMillis() - categoriesCachedAtMs < CATEGORIES_TTL_MS) {
            return@withContext cached
        }
        if (vodApiUrl.isBlank()) return@withContext emptyList()
        val parsed = runCatching {
            val body = fetcher(apiUrl("ac=list"))
            val list = TvBoxParser.parseCategories(body)
            if (list.isEmpty()) TvBoxParser.parseCategories(fetcher(apiUrl("ac=class"))) else list
        }.getOrElse { return@withContext cached.orEmpty() }
        cachedCategories = parsed
        categoriesCachedAtMs = System.currentTimeMillis()
        parsed
    }

    override suspend fun browse(categoryId: String, page: Int): List<VodItem> =
        withContext(Dispatchers.IO) {
            if (vodApiUrl.isBlank()) return@withContext emptyList()
            val query = buildString {
                append("ac=videolist")
                if (categoryId != "all") append("&t=").append(encode(categoryId))
                append("&pg=").append(page.coerceAtLeast(1))
            }
            runCatching { TvBoxParser.parseVodList(fetcher(apiUrl(query))) }
                .getOrDefault(emptyList())
        }

    override suspend fun search(query: String, page: Int): List<VodItem> =
        withContext(Dispatchers.IO) {
            if (vodApiUrl.isBlank() || query.isBlank()) return@withContext emptyList()
            val url = apiUrl("ac=videolist&wd=${encode(query)}&pg=${page.coerceAtLeast(1)}")
            runCatching { TvBoxParser.parseVodList(fetcher(url)) }
                .getOrDefault(emptyList())
        }

    override suspend fun detail(vodId: String): VodDetail? = withContext(Dispatchers.IO) {
        if (vodApiUrl.isBlank()) return@withContext null
        runCatching {
            TvBoxParser.parseDetail(fetcher(apiUrl("ac=detail&ids=${encode(vodId)}")))
        }.getOrNull()
    }

    /** Join base + query, tolerating a base that already ends with `?` or `&`. */
    private fun apiUrl(query: String): String {
        val base = vodApiUrl.trim().trimEnd('/')
        return when {
            base.contains("?") && !base.endsWith("?") && !base.endsWith("&") -> "$base&$query"
            base.endsWith("?") || base.endsWith("&") -> base + query
            else -> "$base?$query"
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private const val CHANNELS_TTL_MS = 30 * 60 * 1000L
        private const val CATEGORIES_TTL_MS = 60 * 60 * 1000L
    }
}
