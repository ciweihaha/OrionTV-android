package com.ciweihaha.oriontv.data.source

import java.net.HttpURLConnection
import java.net.URL

/** Minimal GET helper. [get] is a plain function reference so repositories can inject a fake in tests. */
object HttpFetcher {
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
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
