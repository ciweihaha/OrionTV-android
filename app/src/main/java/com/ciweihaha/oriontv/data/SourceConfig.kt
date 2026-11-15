package com.ciweihaha.oriontv.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Source addresses, editable in Settings. Defaults come from the user's
 * own setup — the live playlist and EPG feed are the same ones the
 * Compose rewrite uses; no third-party VOD source is hard-coded, so
 * [vodApiUrl] starts empty and VOD shows a "not configured" empty state
 * until the user pastes their own TVBox CMS API address.
 */
class SourceConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var liveM3uUrl: String
        get() = prefs.getString(KEY_LIVE_M3U, DEFAULT_LIVE_M3U).orEmpty()
        set(value) = prefs.edit().putString(KEY_LIVE_M3U, value.trim()).apply()

    var vodApiUrl: String
        get() = prefs.getString(KEY_VOD_API, DEFAULT_VOD_API).orEmpty()
        set(value) = prefs.edit().putString(KEY_VOD_API, value.trim()).apply()

    var epgUrl: String
        get() = prefs.getString(KEY_EPG, DEFAULT_EPG).orEmpty()
        set(value) = prefs.edit().putString(KEY_EPG, value.trim()).apply()

    val isVodConfigured: Boolean get() = vodApiUrl.isNotBlank()

    fun resetToDefaults() {
        prefs.edit()
            .putString(KEY_LIVE_M3U, DEFAULT_LIVE_M3U)
            .putString(KEY_VOD_API, DEFAULT_VOD_API)
            .putString(KEY_EPG, DEFAULT_EPG)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "oriontv_settings"
        const val KEY_LIVE_M3U = "live_m3u_url"
        const val KEY_VOD_API = "vod_api_url"
        const val KEY_EPG = "epg_url"

        /** The user's own live playlist (verified healthy, same as mytv uses). */
        const val DEFAULT_LIVE_M3U = "https://live.ciweihaha.win/live.m3u"

        /** No third-party VOD source is hard-coded; the user adds their own. */
        const val DEFAULT_VOD_API = ""

        /** Same XMLTV feed as the Compose rewrite; ids match the M3U tvg-ids. */
        const val DEFAULT_EPG = "https://ysp.ciweihaha.win/epg.xml"
    }
}
