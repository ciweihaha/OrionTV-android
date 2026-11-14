package com.ciweihaha.oriontv.data.model

/** Live TV channel. `streamUrl` is null until a real source is wired in (stage 2). */
data class Channel(
    val id: String,
    val number: String,
    val name: String,
    val group: String,
    val logoUrl: String? = null,
    val streamUrl: String? = null,
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
    /** Null until a real source provides a playable URL (stage 2). */
    val url: String? = null,
)

data class VodDetail(
    val item: VodItem,
    val description: String?,
    val episodes: List<Episode>,
)

/** Home-row action cards (player test stream, update check). */
data class HomeAction(val id: String, val title: String, val subtitle: String)
