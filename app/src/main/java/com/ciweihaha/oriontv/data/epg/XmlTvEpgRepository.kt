package com.ciweihaha.oriontv.data.epg

import com.ciweihaha.oriontv.data.model.Program
import com.ciweihaha.oriontv.data.source.HttpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Stage-2 EPG repository: downloads the XMLTV file once and serves
 * now/next (and full-day) programmes from it.
 *
 * Default feed: https://ysp.ciweihaha.win/epg.xml — the same feed the
 * Compose rewrite uses, and its channel ids are the same `tvg-id` set
 * as the live M3U playlist. The channel's `tvgId` is matched first;
 * if it does not match, the normalised channel name is tried.
 *
 * The feed is fetched on first use and refreshed when the cache is
 * stale (older than 6h, or holding no programme that has not ended yet).
 * A failed refresh keeps serving the last good copy instead of going
 * blank. [channelName] is needed because [Program] lookups in the UI
 * only carry a channel id — callers pass the id and the display name.
 */
class XmlTvEpgRepository(
    private val fetcher: (String) -> String = HttpFetcher::get,
    private val epgUrl: String,
    /**
     * Resolves a channel id to (display name, tvg-id). The name fallback
     * needs the live channel list, so this is a suspend lookup against
     * the source repository (its channel list is cached).
     */
    private val channelLookup: suspend (String) -> Pair<String, String?>? = { null },
) : EpgRepository {

    private val mutex = Mutex()
    private var cached: XmlTvParser.EpgData? = null
    private var cachedAtMs: Long = 0L

    override suspend fun programs(channelId: String, fromMs: Long, toMs: Long): List<Program> {
        val data = load() ?: return emptyList()
        val (name, tvgId) = channelLookup(channelId) ?: (channelId to null)
        return XmlTvParser.programmesForChannel(data, tvgId, name)
            .filter { it.endMs > fromMs && it.startMs < toMs }
            .map { Program(it.title, it.startMs, it.endMs) }
    }

    override suspend fun nowAndNext(channelId: String): Pair<Program?, Program?> {
        val data = load() ?: return null to null
        val (name, tvgId) = channelLookup(channelId) ?: (channelId to null)
        val now = System.currentTimeMillis()
        val current = XmlTvParser.nowPlaying(data, tvgId, name, now)
            ?.let { Program(it.title, it.startMs, it.endMs) }
        val next = XmlTvParser.nextProgramme(data, tvgId, name, now)
            ?.let { Program(it.title, it.startMs, it.endMs) }
        return current to next
    }

    private suspend fun load(): XmlTvParser.EpgData? = mutex.withLock {
        val now = System.currentTimeMillis()
        val current = cached
        if (current != null && XmlTvParser.isCacheFresh(current, cachedAtMs, now)) {
            return@withLock current
        }
        if (epgUrl.isBlank()) return@withLock current
        val fresh = withContext(Dispatchers.IO) {
            runCatching { XmlTvParser.parse(fetcher(epgUrl)) }.getOrNull()
        }
        if (fresh != null) {
            cached = fresh
            cachedAtMs = now
            fresh
        } else {
            current // failed refresh: keep serving the last good copy
        }
    }
}
