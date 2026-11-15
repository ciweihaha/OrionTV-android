package com.ciweihaha.oriontv.data.update

import com.ciweihaha.oriontv.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update check against okys-agg. BOTH endpoints must be preserved —
 * losing either makes the old app's update check fail with
 * 'split of undefined':
 *
 * - https://ysp.ciweihaha.win/agg?ac=oriontv-version
 * - https://ysp.ciweihaha.win/agg/oriontv-version.json
 *
 * The response may be a bare version ("1.7.2") or JSON with a "version"
 * field; parsing is lenient on purpose. Primary is tried first, fallback
 * second. Network runs on Dispatchers.IO; [fetcher] is injectable so
 * tests never touch the network.
 */
class UpdateChecker(
    private val fetcher: (String) -> String = ::defaultFetch,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
) {

    data class Result(val latestVersion: String, val updateAvailable: Boolean)

    suspend fun check(): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        try {
            val body = try {
                fetcher(ENDPOINT_PRIMARY)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Primary failed; try the fallback endpoint.
                fetcher(ENDPOINT_FALLBACK)
            }
            val latest = parseVersion(body)
                ?: throw IllegalStateException("无法解析版本号：$body")
            kotlin.Result.success(Result(latest, compareVersions(latest, currentVersion) > 0))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    companion object {
        const val ENDPOINT_PRIMARY = "https://ysp.ciweihaha.win/agg?ac=oriontv-version"
        const val ENDPOINT_FALLBACK = "https://ysp.ciweihaha.win/agg/oriontv-version.json"

        /** Version responses are a few bytes; anything bigger is not a
         *  version response (previously the body was read unbounded). */
        const val MAX_BODY_BYTES = 64 * 1024

        /** Accepts `{"version":"1.7.2"}` or a bare `1.7.2` (optional leading v). */
        fun parseVersion(body: String): String? {
            val trimmed = body.trim()
            val json = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(trimmed)
            if (json != null) return json.groupValues[1].trim()
            val bare = Regex("^v?(\\d+\\.\\d+\\.\\d+)$").matchEntire(trimmed)
            if (bare != null) return bare.groupValues[1]
            return null
        }

        /** Numeric dotted-version compare: >0 if [a] is newer than [b]. */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
            val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }

        fun defaultFetch(url: String): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "OrionTV-android/${BuildConfig.VERSION_NAME}")
            try {
                val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
                if (declared != null && declared > MAX_BODY_BYTES) {
                    throw IllegalStateException("UPDATE_BODY_TOO_LARGE")
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("HTTP ${connection.responseCode} from $url")
                }
                val bytes = connection.inputStream.readBytesCapped(MAX_BODY_BYTES)
                    ?: throw IllegalStateException("UPDATE_BODY_TOO_LARGE")
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
