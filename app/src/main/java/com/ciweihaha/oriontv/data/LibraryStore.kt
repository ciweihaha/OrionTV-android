package com.ciweihaha.oriontv.data

import android.content.Context
import android.content.SharedPreferences
import com.ciweihaha.oriontv.data.model.FavoriteItem
import com.ciweihaha.oriontv.data.model.WatchRecord
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local library: favourites, watch progress and search history, all in
 * one SharedPreferences file. No server involved — the native rewrite's
 * server-storage branch is out of scope for this repo.
 *
 * JSON encode/decode lives in the companion object as pure functions so
 * the format is unit-tested without Android. Writes are serialised
 * through a lock so a fast double-tap on "favourite" cannot read the
 * same stale snapshot twice.
 */
class LibraryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val writeLock = Any()

    // -- Favourites -------------------------------------------------------

    fun favorites(): List<FavoriteItem> =
        decodeFavorites(prefs.getString(KEY_FAVORITES, null))

    fun isFavorite(vodId: String): Boolean =
        favorites().any { it.vodId == vodId }

    /** Returns true when the item is a favourite after the toggle. */
    fun toggleFavorite(item: FavoriteItem): Boolean = synchronized(writeLock) {
        val current = favorites().toMutableList()
        val existing = current.indexOfFirst { it.vodId == item.vodId }
        val nowFavorite = if (existing >= 0) {
            current.removeAt(existing)
            false
        } else {
            current.add(0, item.copy(savedAtMs = System.currentTimeMillis()))
            true
        }
        prefs.edit().putString(KEY_FAVORITES, encodeFavorites(current)).apply()
        nowFavorite
    }

    // -- Watch progress ---------------------------------------------------

    /** Most recently watched first. */
    fun history(): List<WatchRecord> =
        decodeHistory(prefs.getString(KEY_HISTORY, null))

    fun progressFor(vodId: String, episodeIndex: Int): WatchRecord? =
        history().find { it.vodId == vodId && it.episodeIndex == episodeIndex }

    fun latestProgressFor(vodId: String): WatchRecord? =
        history().filter { it.vodId == vodId }.maxByOrNull { it.updatedAtMs }

    /** Save or update one record. A finished episode (near the end) clears its record. */
    fun saveProgress(record: WatchRecord) = synchronized(writeLock) {
        val current = history().toMutableList()
        current.removeAll { it.vodId == record.vodId && it.episodeIndex == record.episodeIndex }
        val finished = record.durationMs > 0L &&
            record.positionMs >= record.durationMs - FINISHED_MARGIN_MS
        if (!finished && record.positionMs > 0L) {
            current.add(0, record.copy(updatedAtMs = System.currentTimeMillis()))
        }
        val trimmed = current.take(MAX_HISTORY)
        prefs.edit().putString(KEY_HISTORY, encodeHistory(trimmed)).apply()
    }

    fun clearHistory() = synchronized(writeLock) {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    // -- Search history ---------------------------------------------------

    fun searchHistory(): List<String> =
        decodeStringList(prefs.getString(KEY_SEARCH_HISTORY, null))

    fun addSearchQuery(query: String) = synchronized(writeLock) {
        val q = query.trim()
        if (q.isEmpty()) return
        val current = searchHistory().toMutableList()
        current.remove(q)
        current.add(0, q)
        prefs.edit()
            .putString(KEY_SEARCH_HISTORY, encodeStringList(current.take(MAX_SEARCH_HISTORY)))
            .apply()
    }

    fun clearSearchHistory() = synchronized(writeLock) {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    // -- Last live channel -------------------------------------------------

    var lastLiveChannelId: String?
        get() = prefs.getString(KEY_LAST_LIVE_CHANNEL, null)
        set(value) = prefs.edit().putString(KEY_LAST_LIVE_CHANNEL, value).apply()

    companion object {
        const val PREFS_NAME = "oriontv_library"
        const val KEY_FAVORITES = "favorites"
        const val KEY_HISTORY = "history"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val KEY_LAST_LIVE_CHANNEL = "last_live_channel"
        const val MAX_HISTORY = 50
        const val MAX_SEARCH_HISTORY = 20
        const val FINISHED_MARGIN_MS = 60_000L

        // -- Pure JSON codecs (unit-tested) --------------------------------

        fun encodeFavorites(items: List<FavoriteItem>): String {
            val array = JSONArray()
            for (item in items) {
                array.put(
                    JSONObject()
                        .put("vodId", item.vodId)
                        .put("title", item.title)
                        .put("posterUrl", item.posterUrl)
                        .put("year", item.year)
                        .put("remarks", item.remarks)
                        .put("savedAtMs", item.savedAtMs),
                )
            }
            return array.toString()
        }

        fun decodeFavorites(json: String?): List<FavoriteItem> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                List(array.length()) { i ->
                    val obj = array.getJSONObject(i)
                    FavoriteItem(
                        vodId = obj.optString("vodId"),
                        title = obj.optString("title"),
                        posterUrl = obj.optString("posterUrl").ifEmpty { null },
                        year = obj.optString("year").ifEmpty { null },
                        remarks = obj.optString("remarks").ifEmpty { null },
                        savedAtMs = obj.optLong("savedAtMs"),
                    )
                }.filter { it.vodId.isNotEmpty() }
            }.getOrDefault(emptyList())
        }

        fun encodeHistory(records: List<WatchRecord>): String {
            val array = JSONArray()
            for (record in records) {
                array.put(
                    JSONObject()
                        .put("vodId", record.vodId)
                        .put("title", record.title)
                        .put("posterUrl", record.posterUrl)
                        .put("episodeIndex", record.episodeIndex)
                        .put("episodeTitle", record.episodeTitle)
                        .put("positionMs", record.positionMs)
                        .put("durationMs", record.durationMs)
                        .put("updatedAtMs", record.updatedAtMs),
                )
            }
            return array.toString()
        }

        fun decodeHistory(json: String?): List<WatchRecord> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                List(array.length()) { i ->
                    val obj = array.getJSONObject(i)
                    WatchRecord(
                        vodId = obj.optString("vodId"),
                        title = obj.optString("title"),
                        posterUrl = obj.optString("posterUrl").ifEmpty { null },
                        episodeIndex = obj.optInt("episodeIndex", 1),
                        episodeTitle = obj.optString("episodeTitle"),
                        positionMs = obj.optLong("positionMs"),
                        durationMs = obj.optLong("durationMs"),
                        updatedAtMs = obj.optLong("updatedAtMs"),
                    )
                }.filter { it.vodId.isNotEmpty() }
            }.getOrDefault(emptyList())
        }

        fun encodeStringList(items: List<String>): String =
            JSONArray(items).toString()

        fun decodeStringList(json: String?): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                List(array.length()) { i -> array.optString(i) }.filter { it.isNotEmpty() }
            }.getOrDefault(emptyList())
        }
    }
}
