package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.data.LibraryStore
import com.ciweihaha.oriontv.data.model.FavoriteItem
import com.ciweihaha.oriontv.data.model.WatchRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStoreTest {

    @Test
    fun favorites_roundTrip() {
        val items = listOf(
            FavoriteItem("100", "测试影片", "http://x/pic.jpg", "2024", "完结", 1_000L),
            FavoriteItem("101", "第二部", null, null, null, 2_000L),
        )
        val decoded = LibraryStore.decodeFavorites(LibraryStore.encodeFavorites(items))
        assertEquals(items, decoded)
    }

    @Test
    fun favorites_invalidJson_returnsEmpty() {
        assertTrue(LibraryStore.decodeFavorites(null).isEmpty())
        assertTrue(LibraryStore.decodeFavorites("").isEmpty())
        assertTrue(LibraryStore.decodeFavorites("garbage").isEmpty())
        assertTrue(LibraryStore.decodeFavorites("[{\"title\":\"no id\"}]").isEmpty())
    }

    @Test
    fun history_roundTrip() {
        val records = listOf(
            WatchRecord("100", "测试影片", null, 3, "第 3 集", 123_456L, 2_700_000L, 5_000L),
        )
        val decoded = LibraryStore.decodeHistory(LibraryStore.encodeHistory(records))
        assertEquals(records, decoded)
    }

    @Test
    fun history_invalidJson_returnsEmpty() {
        assertTrue(LibraryStore.decodeHistory(null).isEmpty())
        assertTrue(LibraryStore.decodeHistory("{{").isEmpty())
    }

    @Test
    fun searchHistory_roundTripAndInvalid() {
        val queries = listOf("繁花", "庆余年")
        assertEquals(queries, LibraryStore.decodeStringList(LibraryStore.encodeStringList(queries)))
        assertTrue(LibraryStore.decodeStringList("nope").isEmpty())
    }

    @Test
    fun resumePosition_onlyWhenAwayFromStartAndEnd() {
        val mid = WatchRecord("1", "片", null, 1, "第 1 集", 600_000L, 2_700_000L)
        assertEquals(600_000L, mid.resumePositionMs())
        // Too close to the start: start over.
        assertEquals(0L, mid.copy(positionMs = 5_000L).resumePositionMs())
        // Too close to the end: start over.
        assertEquals(0L, mid.copy(positionMs = 2_690_000L).resumePositionMs())
        // Live / unknown duration: no resume.
        assertEquals(0L, mid.copy(durationMs = 0L).resumePositionMs())
    }

    @Test
    fun learnedIntros_roundTrip() {
        val map = mapOf("abc~1" to 120_000L, "xyz~2" to 45_000L)
        assertEquals(map, LibraryStore.decodeLearnedIntros(LibraryStore.encodeLearnedIntros(map)))
    }

    @Test
    fun learnedIntros_invalidJson_returnsEmpty() {
        assertTrue(LibraryStore.decodeLearnedIntros(null).isEmpty())
        assertTrue(LibraryStore.decodeLearnedIntros("").isEmpty())
        assertTrue(LibraryStore.decodeLearnedIntros("garbage").isEmpty())
        // 非正数落点丢弃
        assertTrue(LibraryStore.decodeLearnedIntros("{\"a\":0,\"b\":-5}").isEmpty())
        assertEquals(mapOf("a" to 90_000L), LibraryStore.decodeLearnedIntros("{\"a\":90000,\"b\":0}"))
    }
}
