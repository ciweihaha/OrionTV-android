package com.ciweihaha.oriontv.data.source

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal GET helper. [get] is a plain function reference so repositories
 * can inject a fake in tests.
 *
 * R15-G: the response body used to be read unbounded (`readText()`).
 * Every other fetcher in the app caps the body (agg 2MB, M3U 5MB,
 * update check 64KB) because a broken or hostile TVBox/EPG endpoint
 * returning a multi-GB body would OOM the app. The cap is 5MB, the
 * same as the M3U fetcher — far above any real TVBox list/detail
 * response or the ~413KB EPG file, both of which go through here.
 */
object HttpFetcher {
    /** Largest response body accepted; anything bigger is rejected. */
    const val MAX_BODY_BYTES = 5 * 1024 * 1024

    fun get(url: String, timeoutMs: Int = 15_000): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty(
            "User-Agent",
            "OrionTV-android/0.1.0 (Linux; Android TV)",
        )
        connection.setRequestProperty("Accept", "*/*")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${connection.responseCode} from $url")
            }
            val bytes = connection.inputStream.readBytesCapped(MAX_BODY_BYTES)
                ?: throw IllegalStateException("HTTP body exceeds $MAX_BODY_BYTES bytes from $url")
            return bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    internal fun java.io.InputStream.readBytesCapped(maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
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
