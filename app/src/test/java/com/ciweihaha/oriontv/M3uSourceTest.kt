package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.data.source.M3uDiskCache
import com.ciweihaha.oriontv.data.source.M3uFetcher
import com.ciweihaha.oriontv.data.source.M3uParser
import com.ciweihaha.oriontv.data.source.M3uSource
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class M3uSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val url = "https://example.com/list.m3u"

    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-logo="https://example.com/cctv.png" group-title="央视",CCTV-1 高清
        https://example.com/cctv1.m3u8
        #EXTINF:-1 group-title="卫视",湖南卫视
        https://example.com/hunan.m3u8
    """.trimIndent()

    private fun diskCache() = M3uDiskCache(tempFolder.newFolder("m3u-cache"))

    @Test
    fun `fetch parses a valid playlist`() = runTest {
        val fetcher = M3uFetcher(fetchBytes = { playlist.toByteArray(Charsets.UTF_8) })
        val channels = fetcher.fetchAndParse(url)
        assertEquals(2, channels.size)
        assertEquals("CCTV-1 高清", channels[0].name)
        assertEquals("央视", channels[0].group)
        assertEquals("https://example.com/cctv.png", channels[0].logoUrl)
        assertEquals("001", channels[0].number)
        assertEquals("湖南卫视", channels[1].name)
        assertEquals("卫视", channels[1].group)
    }

    @Test
    fun `fetch returns empty on fetcher failure`() = runTest {
        val fetcher = M3uFetcher(fetchBytes = { throw IllegalStateException("down") })
        assertTrue(fetcher.fetchAndParse(url).isEmpty())
    }

    @Test
    fun `fetch returns empty when body exceeds 5MB`() = runTest {
        val fetcher = M3uFetcher(fetchBytes = { ByteArray(M3uParser.MAX_BYTES + 1) })
        assertTrue(fetcher.fetchAndParse(url).isEmpty())
    }

    @Test
    fun `fetch returns empty for empty body`() = runTest {
        val fetcher = M3uFetcher(fetchBytes = { ByteArray(0) })
        assertTrue(fetcher.fetchAndParse(url).isEmpty())
    }

    @Test
    fun `disk cache round-trips`() = runTest {
        val cache = diskCache()
        val fetcher = M3uFetcher(fetchBytes = { playlist.toByteArray(Charsets.UTF_8) })
        val source = M3uSource(fetcher, cache)
        val refreshed = source.refresh(url)
        assertEquals(2, refreshed.size)
        assertEquals(refreshed, cache.get(url))
        // A fresh instance reads the same disk entry.
        assertEquals(refreshed, M3uSource(fetcher, cache).getCached(url))
    }

    @Test
    fun `disk cache round-trips tvgId`() = runTest {
        // R15-C: tvgId used to be dropped by the cache JSON, so an
        // offline-served channel had no EPG identity.
        val playlistWithId = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" tvg-logo="https://example.com/cctv.png" group-title="央视",CCTV-1 高清
            https://example.com/cctv1.m3u8
        """.trimIndent()
        val cache = diskCache()
        val fetcher = M3uFetcher(fetchBytes = { playlistWithId.toByteArray(Charsets.UTF_8) })
        val source = M3uSource(fetcher, cache)
        val refreshed = source.refresh(url)
        assertEquals(1, refreshed.size)
        assertEquals("cctv1", refreshed[0].tvgId)
        val cached = cache.get(url)
        assertEquals(refreshed, cached)
        assertEquals("cctv1", cached!![0].tvgId)
    }

    @Test
    fun `disk cache expired entry is a miss`() = runTest {
        val cache = diskCache()
        val fetcher = M3uFetcher(fetchBytes = { playlist.toByteArray(Charsets.UTF_8) })
        M3uSource(fetcher, cache).refresh(url)
        val file = cache.cacheFile(url)
        val entry = JSONObject(file.readText(Charsets.UTF_8))
        entry.put("ts", System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1000)
        file.writeText(entry.toString(), Charsets.UTF_8)
        assertNull(cache.get(url))
    }

    @Test
    fun `disk cache corrupt entry is a miss`() {
        val cache = diskCache()
        val file = cache.cacheFile(url)
        file.parentFile.mkdirs()
        file.writeText("{not json", Charsets.UTF_8)
        assertNull(cache.get(url))
    }

    @Test
    fun `disk cache oversized file is a miss`() {
        val cache = diskCache()
        val file = cache.cacheFile(url)
        file.parentFile.mkdirs()
        file.writeBytes(ByteArray(M3uDiskCache.MAX_CACHE_BYTES + 1))
        assertNull(cache.get(url))
    }

    @Test
    fun `getCached serves memory without refetching`() = runTest {
        var calls = 0
        val fetcher = M3uFetcher(fetchBytes = {
            calls++
            playlist.toByteArray(Charsets.UTF_8)
        })
        val source = M3uSource(fetcher, diskCache())
        source.refresh(url)
        assertEquals(1, calls)
        assertEquals(2, source.getCached(url).size)
        assertEquals(1, calls)
    }

    @Test
    fun `refresh failure keeps old cache`() = runTest {
        val cache = diskCache()
        val good = M3uFetcher(fetchBytes = { playlist.toByteArray(Charsets.UTF_8) })
        M3uSource(good, cache).refresh(url)
        val failing = M3uSource(
            M3uFetcher(fetchBytes = { throw IllegalStateException("down") }),
            cache,
        )
        assertTrue(failing.refresh(url).isEmpty())
        assertEquals(2, failing.getCached(url).size)
    }

    @Test
    fun `refresh with empty result does not overwrite cache`() = runTest {
        val cache = diskCache()
        val good = M3uFetcher(fetchBytes = { playlist.toByteArray(Charsets.UTF_8) })
        M3uSource(good, cache).refresh(url)
        val empty = M3uSource(M3uFetcher(fetchBytes = { ByteArray(0) }), cache)
        assertTrue(empty.refresh(url).isEmpty())
        assertEquals(2, empty.getCached(url).size)
    }
}
