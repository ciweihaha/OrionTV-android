package com.ciweihaha.oriontv.data.source

import com.ciweihaha.oriontv.data.model.Episode
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Apple CMS client against the fixed agg endpoint (stage 2, VOD).
 *
 * Mirrors the React Native reference (`API.getAggCategory` in
 * `services/api.ts`) and the live agg responses:
 *
 * - `GET {AGG_BASE_URL}?ac=videolist&t={type}&pg={page}` (page is 1-based)
 *   → `{code: 1, page, pagecount, total, list: [...], class: [{type_id,
 *   type_name}], filters: {...}}`; `t` 1..4 = 电影/电视剧/动漫/综艺.
 * - `GET {AGG_BASE_URL}?ac=detail&ids={vodId}` → single-item `list` with
 *   `vod_content` and `vod_play_from` / `vod_play_url`
 *   (`$$$`-separated source groups, `#`-separated episodes,
 *   `title$url` per episode).
 *
 * Transport rules (same as the reference): 15s timeout, 2MB body cap,
 * up to 3 attempts with 1s backoff; timeout / network error / 5xx /
 * invalid body are retried, 4xx throws immediately. No login, no baseURL.
 * [fetcher] is injectable so tests never touch the network.
 */
class AggCmsClient(
    private val fetcher: (String) -> String = ::defaultFetch,
) {

    data class VideoItem(
        val vodId: String,
        val name: String,
        val pic: String?,
        val year: String?,
        val remarks: String?,
        val doubanScore: String?,
        val typeName: String?,
        val actor: String?,
        val director: String?,
        val blurb: String?,
        val content: String?,
        val playFrom: String,
        val playUrl: String,
    )

    data class VideoClass(val typeId: Int, val typeName: String)

    data class VideoListResult(
        val page: Int,
        val pageCount: Int,
        val total: Int,
        val list: List<VideoItem>,
        val classes: List<VideoClass>,
    )

    suspend fun videolist(type: Int, page: Int): VideoListResult =
        withContext(Dispatchers.IO) {
            val body = getWithRetry("$AGG_BASE_URL?ac=videolist&t=$type&pg=$page")
            parseVideoList(body)
        }

    suspend fun detail(vodId: String): VideoItem? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(vodId, "UTF-8")
        val body = getWithRetry("$AGG_BASE_URL?ac=detail&ids=$encoded")
        val json = JSONObject(body)
        if (json.optInt("code", 0) != 1) throw IllegalStateException("AGG_INVALID_RESPONSE")
        val list = json.optJSONArray("list") ?: return@withContext null
        if (list.length() == 0) return@withContext null
        parseVideoItem(list.getJSONObject(0))
    }

    /**
     * R13-G: keyword search — the Apple CMS `videolist` endpoint takes
     * `wd` (no `t` needed). This is what makes the home "搜索" entry
     * work out of the box: the default VOD is agg, and without this
     * the search screen only ever queried the (usually unconfigured)
     * TVBox source and told the user to configure one.
     */
    suspend fun search(query: String, page: Int): VideoListResult =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val body = getWithRetry("$AGG_BASE_URL?ac=videolist&wd=$encoded&pg=$page")
            parseVideoList(body)
        }

    private suspend fun getWithRetry(url: String): String {
        var lastError: Exception? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            if (attempt > 0) delay(RETRY_DELAY_MS)
            try {
                return fetcher(url)
            } catch (e: CancellationException) {
                // Cancellation is not a failure: never swallow it into a
                // retry, and never keep retrying after destroy.
                throw e
            } catch (e: HttpStatusException) {
                if (e.status in 400..499) throw e
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("AGG_REQUEST_FAILED")
    }

    internal fun parseVideoList(body: String): VideoListResult {
        val json = JSONObject(body)
        if (json.optInt("code", 0) != 1 || json.optJSONArray("list") == null) {
            throw IllegalStateException("AGG_INVALID_RESPONSE")
        }
        val listJson = json.getJSONArray("list")
        val items = (0 until listJson.length()).map { parseVideoItem(listJson.getJSONObject(it)) }
        val classesJson = json.optJSONArray("class")
        val classes = if (classesJson == null) {
            emptyList()
        } else {
            (0 until classesJson.length()).map {
                val c = classesJson.getJSONObject(it)
                VideoClass(c.optInt("type_id", 0), c.optString("type_name", ""))
            }
        }
        return VideoListResult(
            page = json.optInt("page", 1),
            pageCount = json.optInt("pagecount", 1),
            total = json.optInt("total", 0),
            list = items,
            classes = classes,
        )
    }

    internal fun parseVideoItem(json: JSONObject): VideoItem = VideoItem(
        vodId = json.optString("vod_id", ""),
        name = json.optString("vod_name", ""),
        pic = json.optStringOrNull("vod_pic"),
        year = json.optStringOrNull("vod_year"),
        remarks = json.optStringOrNull("vod_remarks"),
        doubanScore = json.optStringOrNull("vod_douban_score"),
        typeName = json.optStringOrNull("type_name"),
        actor = json.optStringOrNull("vod_actor"),
        director = json.optStringOrNull("vod_director"),
        blurb = json.optStringOrNull("vod_blurb"),
        content = json.optStringOrNull("vod_content"),
        playFrom = json.optString("vod_play_from", ""),
        playUrl = json.optString("vod_play_url", ""),
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        optString(key, "").takeIf { it.isNotEmpty() }

    /**
     * Episodes from `vod_play_from` / `vod_play_url`, ALL `$$$` source
     * groups (M12 — previously only the first group was parsed, so a
     * title with several sources could only ever play the first one).
     * Groups are `$$$`-separated, episodes `#`-separated, each episode
     * is `title$url`; the group name comes from the matching
     * `vod_play_from` entry ("线路 N" when it is missing). An episode
     * without `$` keeps its text as the URL and gets a generated
     * "第 N 集" title. Indices restart per source group.
     */
    fun parseEpisodes(playFrom: String, playUrl: String): List<Episode> {
        if (playUrl.isEmpty()) return emptyList()
        val sourceNames = playFrom.split("\$\$\$").map { it.trim() }
        val episodes = mutableListOf<Episode>()
        playUrl.split("\$\$\$").forEachIndexed { groupIndex, group ->
            if (group.isEmpty()) return@forEachIndexed
            val source = sourceNames.getOrNull(groupIndex)?.takeIf { it.isNotEmpty() }
                ?: "线路 ${groupIndex + 1}"
            group.split("#").forEachIndexed { index, segment ->
                if (segment.isEmpty()) return@forEachIndexed
                val dollar = segment.indexOf('$')
                if (dollar == -1) {
                    episodes += Episode(index + 1, "第 ${index + 1} 集", segment, source)
                } else {
                    val title = segment.substring(0, dollar).ifEmpty { "第 ${index + 1} 集" }
                    episodes += Episode(index + 1, title, segment.substring(dollar + 1), source)
                }
            }
        }
        return episodes
    }

    companion object {
        const val AGG_BASE_URL = "https://ysp.ciweihaha.win/agg"
        const val DEFAULT_TIMEOUT_MS = 15_000
        const val MAX_JSON_BYTES = 2 * 1024 * 1024
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1_000L

        class HttpStatusException(val status: Int, message: String) : Exception(message)

        fun defaultFetch(url: String): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = DEFAULT_TIMEOUT_MS
            connection.readTimeout = DEFAULT_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "OrionTV-android/0.1.0")
            try {
                val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
                if (declared != null && declared > MAX_JSON_BYTES) {
                    throw IllegalStateException("AGG_BODY_TOO_LARGE")
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw HttpStatusException(
                        connection.responseCode,
                        "HTTP ${connection.responseCode} from $url",
                    )
                }
                val bytes = connection.inputStream.readBytesCapped(MAX_JSON_BYTES)
                    ?: throw IllegalStateException("AGG_BODY_TOO_LARGE")
                return bytes.toString(Charsets.UTF_8)
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
