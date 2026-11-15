package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.data.source.TvBoxSourceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvBoxSourceRepositoryTest {

    private val m3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="cctv1" group-title="央视",CCTV-1
        http://example.com/cctv1.m3u8
    """.trimIndent()

    private val categoriesJson = """{"class":[{"type_id":"1","type_name":"电影"}]}"""
    private val listJson = """{"list":[
        {"vod_id":"100","vod_name":"测试影片","vod_pic":"","vod_year":"2024","vod_remarks":"完结"}
    ]}"""
    private val detailJson = """{"list":[{
        "vod_id":"100","vod_name":"测试影片","vod_content":"简介",
        "vod_play_from":"线路A","vod_play_url":"第1集${'$'}http://a/1.m3u8"
    }]}"""

    private fun repository(requested: MutableList<String>): TvBoxSourceRepository {
        val fetcher: (String) -> String = { url ->
            requested += url
            when {
                url == "https://live.example.com/live.m3u" -> m3u
                url.contains("ac=list") -> categoriesJson
                url.contains("ac=videolist") && url.contains("wd=") -> listJson
                url.contains("ac=videolist") -> listJson
                url.contains("ac=detail") -> detailJson
                else -> throw IllegalStateException("unexpected url: $url")
            }
        }
        return TvBoxSourceRepository(
            fetcher = fetcher,
            liveM3uUrl = "https://live.example.com/live.m3u",
            vodApiUrl = "https://vod.example.com/api.php/provide/vod/",
        )
    }

    @Test
    fun channels_parsesM3uAndCaches() = runTest {
        val requested = mutableListOf<String>()
        val repo = repository(requested)
        val channels = repo.channels()
        assertEquals(1, channels.size)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("cctv1", channels[0].tvgId)
        repo.channels()
        assertEquals(1, requested.count { it.contains("live.m3u") })
    }

    @Test
    fun categories_browse_search_detail_useCmsEndpoints() = runTest {
        val requested = mutableListOf<String>()
        val repo = repository(requested)
        assertEquals("电影", repo.categories()[0].name)
        assertEquals("测试影片", repo.browse("1", 1)[0].title)
        assertEquals("测试影片", repo.search("测试", 1)[0].title)
        val detail = repo.detail("100")
        assertEquals("简介", detail?.description)
        assertEquals("第1集", detail?.episodes?.get(0)?.title)
        assertEquals("http://a/1.m3u8", detail?.episodes?.get(0)?.url)
        assertTrue(requested.any { it.contains("ac=list") })
        assertTrue(requested.any { it.contains("ac=videolist&t=1&pg=1") })
        assertTrue(requested.any { it.contains("ac=videolist&wd=") })
        assertTrue(requested.any { it.contains("ac=detail&ids=100") })
    }

    @Test
    fun unconfiguredVod_returnsEmptyWithoutFetching() = runTest {
        var fetched = false
        val repo = TvBoxSourceRepository(
            fetcher = { fetched = true; "" },
            liveM3uUrl = "",
            vodApiUrl = "",
        )
        assertTrue(repo.channels().isEmpty())
        assertTrue(repo.categories().isEmpty())
        assertTrue(repo.browse("all", 1).isEmpty())
        assertTrue(repo.search("x", 1).isEmpty())
        assertEquals(null, repo.detail("1"))
        assertTrue(!fetched)
    }

    @Test
    fun fetcherFailure_returnsCachedOrEmpty() = runTest {
        val repo = TvBoxSourceRepository(
            fetcher = { throw IllegalStateException("network down") },
            liveM3uUrl = "https://live.example.com/live.m3u",
            vodApiUrl = "https://vod.example.com/api.php/provide/vod/",
        )
        assertTrue(repo.channels().isEmpty())
        assertTrue(repo.categories().isEmpty())
        assertTrue(repo.browse("1", 1).isEmpty())
        assertEquals(null, repo.detail("1"))
    }
}
