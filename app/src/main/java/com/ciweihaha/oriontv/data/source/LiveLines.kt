package com.ciweihaha.oriontv.data.source

import com.ciweihaha.oriontv.data.model.Channel

/**
 * Live multi-line support (round 9).
 *
 * An M3U playlist can list the same channel name several times with
 * different stream URLs (one entry per line). The parser keeps each as
 * a separate [Channel]; this groups them back into the lines of one
 * channel so the player can switch between them without leaving
 * playback. Pure logic, no Android dependency, so it is unit-testable.
 */
object LiveLines {

    /**
     * All playable lines of [channel], in playlist order: every channel
     * in [channels] with the exact same name and a non-null stream URL.
     * Returns a single-element list (the channel itself) when it has no
     * alternate lines, and an empty list when the channel itself is not
     * playable.
     */
    fun linesFor(channels: List<Channel>, channel: Channel): List<Channel> {
        if (channel.streamUrl.isNullOrBlank()) return emptyList()
        return channels.filter { it.name == channel.name && !it.streamUrl.isNullOrBlank() }
    }

    /** Index of [channel] inside its own [lines] (its stream URL identifies it). */
    fun indexOf(lines: List<Channel>, channel: Channel): Int =
        lines.indexOfFirst { it.streamUrl == channel.streamUrl }.coerceAtLeast(0)

    /** Next line index, wrapping around to the first line. */
    fun nextIndex(current: Int, total: Int): Int {
        if (total <= 0) return 0
        return (current + 1) % total
    }
}
