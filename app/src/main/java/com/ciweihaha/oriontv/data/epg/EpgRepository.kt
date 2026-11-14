package com.ciweihaha.oriontv.data.epg

import com.ciweihaha.oriontv.data.model.Program
import com.ciweihaha.oriontv.data.placeholder.PlaceholderData

/**
 * EPG access. Stage 2 replaces [PlaceholderEpgRepository] with a real
 * XMLTV / source EPG parser; screens only depend on this interface.
 */
interface EpgRepository {
    suspend fun programs(channelId: String, fromMs: Long, toMs: Long): List<Program>
    suspend fun nowAndNext(channelId: String): Pair<Program?, Program?>
}

/** Stage-1 placeholder: derives demo now/next programmes from the clock. */
class PlaceholderEpgRepository : EpgRepository {
    override suspend fun programs(channelId: String, fromMs: Long, toMs: Long): List<Program> =
        PlaceholderData.programs(channelId, fromMs, toMs)

    override suspend fun nowAndNext(channelId: String): Pair<Program?, Program?> {
        val now = System.currentTimeMillis()
        val list = PlaceholderData.programs(channelId, now - 3_600_000L, now + 7_200_000L)
        val current = list.find { it.isLive(now) }
        val next = list.firstOrNull { it.startMs > now }
        return current to next
    }
}
