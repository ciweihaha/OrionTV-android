package com.ciweihaha.oriontv.data.settings

import android.content.Context
import android.content.SharedPreferences
import java.net.URL

/**
 * Normalize a user-typed M3U address: trim, default to https when no
 * scheme is given, require http(s) with a host. Returns null when the
 * input cannot be used as a playlist URL. Pure logic, unit-tested.
 */
fun normalizeM3uUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    // java.net.URL is lenient about spaces ("https://not a url" parses),
    // so reject whitespace explicitly: it can never be a usable address.
    if (trimmed.any { it.isWhitespace() }) return null
    val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    return try {
        val url = URL(withScheme)
        if (url.protocol != "http" && url.protocol != "https") return null
        if (url.host.isNullOrEmpty()) return null
        withScheme
    } catch (e: Exception) {
        null
    }
}

/** App settings backed by SharedPreferences. M3U live source + skip-intro. */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getM3uUrl(): String? = prefs.getString(KEY_M3U_URL, null)?.takeIf { it.isNotBlank() }

    fun setM3uUrl(url: String?) {
        prefs.edit().putString(KEY_M3U_URL, url).apply()
    }

    /**
     * 默认片头时长（秒）：点播没有学过的片头落点时，跳过按钮按这个窗口显示；
     * 0 表示不跳过（不显示跳过按钮）。与原生版 INTRO_SKIP_SECONDS 默认值一致。
     */
    fun getSkipIntroSeconds(): Int =
        prefs.getInt(KEY_SKIP_INTRO_SECONDS, DEFAULT_SKIP_INTRO_SECONDS)

    fun setSkipIntroSeconds(seconds: Int) {
        prefs.edit()
            .putInt(KEY_SKIP_INTRO_SECONDS, seconds.coerceIn(0, MAX_SKIP_INTRO_SECONDS))
            .apply()
    }

    companion object {
        const val PREFS_NAME = "oriontv_settings"
        const val KEY_M3U_URL = "m3u_url"
        const val KEY_SKIP_INTRO_SECONDS = "skip_intro_seconds"
        const val DEFAULT_SKIP_INTRO_SECONDS = 90
        const val MAX_SKIP_INTRO_SECONDS = 600
    }
}
