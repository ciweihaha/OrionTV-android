package com.ciweihaha.oriontv.data.source

import com.ciweihaha.oriontv.data.model.Channel
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * M3U live source: fetch + two-level cache (stage 2).
 *
 * Mirrors the React Native reference (`services/m3u.ts`:
 * `fetchAndParseM3u` / `getCachedM3u` / `refreshM3u`,
 * `M3uCacheManager` in `services/storage.ts`):
 *
 * - Fetch: 15s timeout, 5MB body cap (Content-Length precheck + capped
 *   stream read), any failure → empty list, never throws.
 * - Cache: memory first, then disk (one JSON file per URL, 7-day TTL,
 *   3MB entry cap); corrupt / expired / oversized entries are a miss.
 * - Refresh writes both caches only when the fetch returns a non-empty
 *   list; a failed or empty refresh leaves the old cache untouched.
 *
 * [fetchBytes] and the disk directory are injectable so tests never
 * touch the network or the app's real cache dir.
 */
class M3uFetcher(
    private val fetchBytes: (String) -> ByteArray = ::defaultFetchBytes,
) {

    /** Fetch + parse; never throws — any failure yields an empty list. */
    suspend fun fetchAndParse(m3uUrl: String): List<Channel> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = fetchBytes(m3uUrl)
                if (bytes.size > M3uParser.MAX_BYTES) return@runCatching emptyList<Channel>()
                M3uParser.parse(bytes.toString(Charsets.UTF_8))
            }.getOrDefault(emptyList())
        }

    companion object {
        const val FETCH_TIMEOUT_MS = 15_000

        fun defaultFetchBytes(m3uUrl: String): ByteArray {
            val connection = URL(m3uUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = FETCH_TIMEOUT_MS
            connection.readTimeout = FETCH_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "OrionTV-android/0.1.0")
            try {
                val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
                if (declared != null && declared > M3uParser.MAX_BYTES) {
                    throw IllegalStateException("M3U_BODY_TOO_LARGE")
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("HTTP ${connection.responseCode} from $m3uUrl")
                }
                return connection.inputStream.readBytesCapped(M3uParser.MAX_BYTES)
                    ?: throw IllegalStateException("M3U_BODY_TOO_LARGE")
            } finally {
                connection.disconnect()
            }
        }

        private fun java.io.InputStream.readBytesCapped(maxBytes: Int): ByteArray? {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = read(buffer)
                if (n == -1) break
                total += n
                if (total > maxBytes) return null
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }
}

/** Disk side of the M3U cache: one `{sha256(url)}.json` per playlist. */
class M3uDiskCache(private val cacheDir: File) {

    /** Null on miss / expired / corrupt / oversized entry. Never throws. */
    fun get(m3uUrl: String): List<Channel>? = runCatching {
        val file = cacheFile(m3uUrl)
        if (!file.exists() || file.length() > MAX_CACHE_BYTES) return null
        val entry = JSONObject(file.readText(Charsets.UTF_8))
        val ts = entry.optLong("ts", Long.MIN_VALUE)
        if (ts == Long.MIN_VALUE || System.currentTimeMillis() - ts > CACHE_TTL_MS) return null
        val array = entry.optJSONArray("channels") ?: return null
        val channels = (0 until array.length()).mapNotNull { i ->
            (array.optJSONObject(i) ?: return@mapNotNull null).toChannel()
        }
        channels.ifEmpty { null }
    }.getOrNull()

    /** Writes `{ts, channels}`; oversized entries are skipped, never throws. */
    fun save(m3uUrl: String, channels: List<Channel>) = runCatching {
        val entry = JSONObject()
        entry.put("ts", System.currentTimeMillis())
        val array = JSONArray()
        channels.forEach { array.put(it.toJson()) }
        entry.put("channels", array)
        val json = entry.toString()
        if (json.toByteArray(Charsets.UTF_8).size > MAX_CACHE_BYTES) return
        cacheDir.mkdirs()
        cacheFile(m3uUrl).writeText(json, Charsets.UTF_8)
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { if (it.name.endsWith(".json")) it.delete() }
    }

    internal fun cacheFile(m3uUrl: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(m3uUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "m3u_$digest.json")
    }

    private fun Channel.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("number", number)
        put("name", name)
        put("group", group)
        put("logo", logoUrl ?: "")
        put("url", streamUrl ?: "")
    }

    private fun JSONObject.toChannel(): Channel? {
        val url = optString("url", "")
        if (url.isEmpty()) return null
        return Channel(
            id = optString("id", url),
            number = optString("number", ""),
            name = optString("name", "Unknown"),
            group = optString("group", "Default"),
            logoUrl = optString("logo", "").takeIf { it.isNotEmpty() },
            streamUrl = url,
        )
    }

    companion object {
        /** 7 days, same as the reference. */
        const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

        /** 3MB per entry, same as the reference. */
        const val MAX_CACHE_BYTES = 3 * 1024 * 1024
    }
}

/**
 * Cached M3U access: [getCached] serves memory → disk (instant render),
 * [refresh] fetches from the network and updates both caches.
 * A failed or empty refresh returns an empty list and leaves the old
 * cache untouched, so the UI can keep showing stale channels.
 */
class M3uSource(
    private val fetcher: M3uFetcher = M3uFetcher(),
    private val diskCache: M3uDiskCache,
) {

    private val memCache = mutableMapOf<String, List<Channel>>()

    suspend fun getCached(m3uUrl: String): List<Channel> {
        memCache[m3uUrl]?.takeIf { it.isNotEmpty() }?.let { return it }
        val disk = diskCache.get(m3uUrl)
        if (!disk.isNullOrEmpty()) {
            memCache[m3uUrl] = disk
            return disk
        }
        return emptyList()
    }

    suspend fun refresh(m3uUrl: String): List<Channel> {
        val channels = fetcher.fetchAndParse(m3uUrl)
        if (channels.isNotEmpty()) {
            memCache[m3uUrl] = channels
            runCatching { diskCache.save(m3uUrl, channels) }
        }
        return channels
    }
}
