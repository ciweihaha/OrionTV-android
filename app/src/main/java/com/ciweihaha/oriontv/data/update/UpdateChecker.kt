package com.ciweihaha.oriontv.data.update

import com.ciweihaha.oriontv.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update check for THIS app (the android rewrite) against its own
 * GitHub releases.
 *
 * R15-A: the old code checked the agg version feed
 * (`?ac=oriontv-version` / `oriontv-version.json`), which tracks the
 * OLD 1.7.x app — it returns `{"version":"1.7.2"}`. This rewrite is
 * versioned 0.1.0, so `compareVersions("1.7.2", "0.1.0") > 0` was
 * ALWAYS true: every tap on "检查更新" reported "发现新版本 1.7.2",
 * and "去下载" opened this repo's releases page, which has no 1.7.2
 * at all. Same class of bug as the mytv fork's version-mixing issue
 * (bug ledger #8): the two sides of a version comparison must use
 * one numbering scheme. The agg feed cannot answer "is there a newer
 * build of THIS app", so the check now reads this repo's own
 * releases instead — the same source the "去下载" button opens.
 *
 * How the latest tag is found (same pattern the UnBox repo uses):
 * - Primary: `GET https://github.com/ciweihaha/OrionTV-android/releases/latest`
 *   with a HEAD request; GitHub 302-redirects to the newest release's
 *   tag page (`/releases/tag/vX.Y.Z`), or to `/releases` when the repo
 *   has no releases at all. No API rate limit is involved.
 * - Fallback: the GitHub Releases API (`/releases/latest`), parsing
 *   `tag_name`. A 404 there also means "no releases yet".
 * With no releases at all there is nothing newer by definition, so
 * the check reports the current version as latest (no update) instead
 * of failing. A real network failure still fails the check.
 *
 * [latestTag] is injectable so tests never touch the network.
 */
class UpdateChecker(
    private val latestTag: (String) -> String = ::defaultLatestTag,
    private val currentVersion: String = BuildConfig.VERSION_NAME,
) {

    data class Result(val latestVersion: String, val updateAvailable: Boolean)

    suspend fun check(): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        try {
            val latest = latestTag(currentVersion)
            kotlin.Result.success(Result(latest, compareVersions(latest, currentVersion) > 0))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    companion object {
        /** Releases page: GitHub redirects this to the newest tag page. */
        const val RELEASES_LATEST_URL =
            "https://github.com/ciweihaha/OrionTV-android/releases/latest"

        /** GitHub Releases API fallback (unauthenticated, 60/hour). */
        const val RELEASES_API_URL =
            "https://api.github.com/repos/ciweihaha/OrionTV-android/releases/latest"

        /** Version responses are a few bytes; anything bigger is not a
         *  version response (previously the body was read unbounded). */
        const val MAX_BODY_BYTES = 64 * 1024

        /**
         * Tag of the newest release of this repo, or [currentVersion]
         * when the repo has no releases yet (nothing newer can exist).
         * Throws on a real network failure so the UI can say the check
         * failed instead of silently reporting "no update".
         */
        fun defaultLatestTag(currentVersion: String): String {
            resolveTagFromRedirect()?.let { return it }
            return resolveTagFromApi() ?: currentVersion
        }

        /**
         * Follows the releases/latest redirect with a HEAD request and
         * reads the tag from the landing URL (`/releases/tag/vX.Y.Z`).
         * Returns null when the repo has no releases (GitHub lands on
         * `/releases` with no tag in the URL) or when the request
         * itself fails — the caller falls back to the API.
         */
        internal fun resolveTagFromRedirect(
            openConnection: (String) -> HttpURLConnection = ::openConnection,
        ): String? = runCatching {
            val connection = openConnection(RELEASES_LATEST_URL)
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "User-Agent",
                "OrionTV-android/${BuildConfig.VERSION_NAME}",
            )
            try {
                // Triggers the request (and the redirect chain); a HEAD
                // response has no body to download.
                connection.responseCode
                parseTagFromUrl(connection.url.toString())
            } finally {
                connection.disconnect()
            }
        }.getOrNull()

        /** Tag from a `/releases/tag/<tag>` URL; null for any other URL. */
        internal fun parseTagFromUrl(finalUrl: String): String? {
            val path = runCatching { URL(finalUrl).path }.getOrNull() ?: return null
            val marker = "/releases/tag/"
            val index = path.indexOf(marker)
            if (index == -1) return null
            return path.substring(index + marker.length)
                .substringBefore("/")
                .takeIf { it.isNotEmpty() }
                ?.removePrefix("v")
        }

        /**
         * GitHub Releases API: 200 → `tag_name`; 404 → no releases yet
         * (null, not an error); anything else throws.
         */
        internal fun resolveTagFromApi(
            openConnection: (String) -> HttpURLConnection = ::openConnection,
        ): String? {
            val connection = openConnection(RELEASES_API_URL)
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty(
                "User-Agent",
                "OrionTV-android/${BuildConfig.VERSION_NAME}",
            )
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val bytes = connection.inputStream.readBytesCapped(MAX_BODY_BYTES)
                            ?: throw IllegalStateException("UPDATE_BODY_TOO_LARGE")
                        return parseVersion(bytes.toString(Charsets.UTF_8))
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> return null
                    else -> throw IllegalStateException(
                        "HTTP ${connection.responseCode} from $RELEASES_API_URL",
                    )
                }
            } finally {
                connection.disconnect()
            }
        }

        private fun openConnection(url: String): HttpURLConnection =
            URL(url).openConnection() as HttpURLConnection

        /**
         * Accepts `{"version":"1.7.2"}`, `{"tag_name":"v0.2.0"}` or a
         * bare `1.7.2` (optional leading v).
         */
        fun parseVersion(body: String): String? {
            val trimmed = body.trim()
            val json = Regex("\"(?:version|tag_name)\"\\s*:\\s*\"v?([^\"]+)\"").find(trimmed)
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
