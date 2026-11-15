package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.data.source.TvBoxParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvBoxParserTest {

    @Test
    fun parseCategories_readsClassArray() {
        val json = """{"class":[
            {"type_id":"1","type_name":"电影"},
            {"type_id":"2","type_name":"电视剧"},
            {"type_id":"","type_name":"空id"},
            {"type_id":"3","type_name":""}
        ]}"""
        val categories = TvBoxParser.parseCategories(json)
        assertEquals(listOf("电影", "电视剧"), categories.map { it.name })
        assertEquals(listOf("1", "2"), categories.map { it.id })
    }

    @Test
    fun parseCategories_invalidJson_returnsEmpty() {
        assertTrue(TvBoxParser.parseCategories("not json").isEmpty())
        assertTrue(TvBoxParser.parseCategories("{}").isEmpty())
    }

    @Test
    fun parseVodList_readsItems() {
        val json = """{"list":[
            {"vod_id":"100","vod_name":"测试影片","vod_pic":"http://x/pic.jpg","vod_year":"2024","vod_remarks":"完结"},
            {"vod_id":"101","vod_name":"无海报","vod_pic":"","vod_year":"","vod_remarks":""}
        ],"page":1,"pagecount":5}"""
        val items = TvBoxParser.parseVodList(json)
        assertEquals(2, items.size)
        assertEquals("100", items[0].id)
        assertEquals("测试影片", items[0].title)
        assertEquals("http://x/pic.jpg", items[0].posterUrl)
        assertEquals("2024", items[0].year)
        assertEquals("完结", items[0].remarks)
        assertNull(items[1].posterUrl)
        assertNull(items[1].year)
        assertNull(items[1].remarks)
    }

    @Test
    fun parsePlaySources_splitsSourcesAndEpisodes() {
        val sources = TvBoxParser.parsePlaySources(
            "线路A${'$'}${'$'}${'$'}线路B",
            "第1集${'$'}http://a/1.m3u8#第2集${'$'}http://a/2.m3u8${'$'}${'$'}${'$'}第1集${'$'}http://b/1.m3u8",
        )
        assertEquals(2, sources.size)
        assertEquals("线路A", sources[0].first)
        assertEquals(listOf("第1集", "第2集"), sources[0].second.map { it.title })
        assertEquals("http://a/2.m3u8", sources[0].second[1].url)
        assertEquals("线路B", sources[1].first)
        assertEquals(1, sources[1].second.size)
    }

    @Test
    fun parsePlaySources_bareUrlBecomesSingleEpisode() {
        val sources = TvBoxParser.parsePlaySources("", "http://x/movie.mp4")
        assertEquals(1, sources.size)
        assertEquals(1, sources[0].second.size)
        assertEquals("正片", sources[0].second[0].title)
        assertEquals("http://x/movie.mp4", sources[0].second[0].url)
    }

    @Test
    fun parsePlaySources_emptyUrlReturnsEmpty() {
        assertTrue(TvBoxParser.parsePlaySources("线路A", "").isEmpty())
        assertTrue(TvBoxParser.parsePlaySources("", "   ").isEmpty())
    }

    @Test
    fun parseDetail_flattensMultiSourceWithPrefix() {
        val json = """{"list":[{
            "vod_id":"100","vod_name":"测试影片","vod_pic":"","vod_year":"2024","vod_remarks":"完结",
            "vod_content":"简介内容",
            "vod_play_from":"线路A${'$'}${'$'}${'$'}线路B",
            "vod_play_url":"第1集${'$'}http://a/1.m3u8#第2集${'$'}http://a/2.m3u8${'$'}${'$'}${'$'}第1集${'$'}http://b/1.m3u8"
        }]}"""
        val detail = TvBoxParser.parseDetail(json)
        assertEquals("测试影片", detail?.item?.title)
        assertEquals("简介内容", detail?.description)
        assertEquals(
            listOf("线路A 第1集", "线路A 第2集", "线路B 第1集"),
            detail?.episodes?.map { it.title },
        )
        assertEquals("http://b/1.m3u8", detail?.episodes?.get(2)?.url)
    }

    @Test
    fun parseDetail_singleSourceKeepsPlainTitles() {
        val json = """{"list":[{
            "vod_id":"7","vod_name":"单线路","vod_content":"",
            "vod_play_from":"默认",
            "vod_play_url":"第1集${'$'}http://a/1.m3u8#第2集${'$'}http://a/2.m3u8"
        }]}"""
        val detail = TvBoxParser.parseDetail(json)
        assertEquals(listOf("第1集", "第2集"), detail?.episodes?.map { it.title })
        assertNull(detail?.description)
    }

    @Test
    fun parseDetail_emptyListReturnsNull() {
        assertNull(TvBoxParser.parseDetail("""{"list":[]}"""))
        assertNull(TvBoxParser.parseDetail("garbage"))
    }
}
