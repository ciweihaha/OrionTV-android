package com.ciweihaha.oriontv.data.model

/** Live TV channel. `streamUrl` is null until a real source is wired in (stage 2). */
data class Channel(
    val id: String,
    val number: String,
    val name: String,
    val group: String,
    val logoUrl: String? = null,
    val streamUrl: String? = null,
    /** The `tvg-id` from the M3U `#EXTINF:` line (round 1); null when the playlist has none. */
    val tvgId: String? = null,
)

/** One EPG programme. Times are epoch milliseconds. */
data class Program(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val description: String? = null,
) {
    fun isLive(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs in startMs until endMs

    /** 0..1 progress of the programme at [nowMs]; 0 before start, 1 after end. */
    fun progress(nowMs: Long = System.currentTimeMillis()): Float {
        if (endMs <= startMs) return 0f
        return ((nowMs - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
    }
}

data class VodCategory(val id: String, val name: String)

data class VodItem(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val year: String? = null,
    val remarks: String? = null,
)

data class Episode(
    val index: Int,
    val title: String,
    /** Playable URL; null for placeholder episodes without one. */
    val url: String? = null,
    /** Which `$$$` source group this episode came from (M12); null for
     *  placeholder episodes that have no source. */
    val source: String? = null,
)

data class VodDetail(
    val item: VodItem,
    val description: String?,
    val episodes: List<Episode>,
)

/** One page of a VOD browse/search result. `hasMore` is false on the last page. */
data class VodPage(
    val items: List<VodItem>,
    val hasMore: Boolean,
)

/** A favourited title (round 1, LibraryStore). */
data class FavoriteItem(
    val vodId: String,
    val title: String,
    val posterUrl: String? = null,
    val year: String? = null,
    val remarks: String? = null,
    val savedAtMs: Long = 0L,
)

/** One watch-progress record (round 1, LibraryStore). */
data class WatchRecord(
    val vodId: String,
    val title: String,
    val posterUrl: String? = null,
    val episodeIndex: Int = 1,
    val episodeTitle: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAtMs: Long = 0L,
) {
    /**
     * Resume position: only when the viewer is genuinely mid-episode.
     * Too close to the start (under [RESUME_START_THRESHOLD_MS]) or too
     * close to the end (within [RESUME_END_MARGIN_MS] of it), or with an
     * unknown duration (live), start from the beginning instead.
     */
    fun resumePositionMs(): Long {
        if (durationMs <= 0L) return 0L
        if (positionMs < RESUME_START_THRESHOLD_MS) return 0L
        if (positionMs >= durationMs - RESUME_END_MARGIN_MS) return 0L
        return positionMs
    }

    companion object {
        /** Under 10s in: treat as not started. */
        const val RESUME_START_THRESHOLD_MS = 10_000L

        /** Within 60s of the end: treat as finished. */
        const val RESUME_END_MARGIN_MS = 60_000L
    }
}

/** Home-row action cards (player test stream, update check). */
data class HomeAction(val id: String, val title: String, val subtitle: String)
