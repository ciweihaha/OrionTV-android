package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.data.source.AggCmsClient
import com.ciweihaha.oriontv.data.source.AggSourceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AggCmsClientTest {

    private val listJson = """
        {"code":1,"msg":"数据列表","page":1,"pagecount":700,"limit":60,"total":36151,
        "list":[
          {"vod_id":"src~111","vod_name":"阿龙2025","vod_pic":"https://example.com/a.jpg",
           "vod_year":"2025","vod_remarks":"HD","vod_douban_score":"6.0","type_name":"电影片",
           "vod_actor":"郑中基","vod_director":"郑中基","vod_blurb":"简介"},
          {"vod_id":"src~222","vod_name":"无海报","vod_pic":"","vod_year":"","vod_remarks":"",
           "vod_douban_score":"","type_name":"动作片"}
        ],
        "class":[{"type_id":1,"type_name":"电影"},{"type_id":2,"type_name":"电视剧"}]}
    """.trimIndent()

    private val detailJson = """
        {"code":1,"msg":"数据详情","page":1,"pagecount":1,"limit":1,"total":1,
        "list":[
          {"vod_id":"src~111","vod_name":"阿龙2025",
           "vod_pic":"https://example.com/a.jpg","vod_year":"2025","vod_remarks":"HD",
           "vod_douban_score":"6.0","type_name":"电影片",
           "vod_content":"完整简介","vod_blurb":"短简介",
           "vod_play_from":"lzm3u8${'$'}${'$'}${'$'}liangzi",
           "vod_play_url":"国语中字${'$'}https://example.com/a.m3u8#粤语中字${'$'}https://example.com/b.m3u8${'$'}${'$'}${'$'}国语中字${'$'}https://example.com/c.m3u8"}
        ]}
    """.trimIndent()

    @Test
    fun `videolist maps items and classes`() = runTest {
        val client = AggCmsClient(fetcher = { listJson })
        val result = client.videolist(1, 1)
        assertEquals(1, result.page)
        assertEquals(700, result.pageCount)
        assertEquals(36151, result.total)
        assertEquals(2, result.list.size)
        val first = result.list[0]
        assertEquals("src~111", first.vodId)
        assertEquals("阿龙2025", first.name)
        assertEquals("https://example.com/a.jpg", first.pic)
        assertEquals("2025", first.year)
        assertEquals("HD", first.remarks)
        assertEquals("6.0", first.doubanScore)
        assertEquals("电影片", first.typeName)
        assertNull(result.list[1].pic)
        assertNull(result.list[1].year)
        assertEquals(listOf("电影", "电视剧"), result.classes.map { it.typeName })
        assertEquals(listOf(1, 2), result.classes.map { it.typeId })
    }

    @Test
    fun `invalid code throws`() = runTest {
        val client = AggCmsClient(fetcher = { """{"code":0,"msg":"err","list":[]}""" })
        var thrown = false
        try {
            client.videolist(1, 1)
        } catch (e: IllegalStateException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun `retries twice then succeeds`() = runTest {
        var calls = 0
        val client = AggCmsClient(fetcher = {
            calls++
            if (calls < 3) throw IllegalStateException("boom")
            listJson
        })
        val result = client.videolist(1, 1)
        assertEquals(3, calls)
        assertEquals(2, result.list.size)
    }

    @Test
    fun `gives up after three failures`() = runTest {
        var calls = 0
        val client = AggCmsClient(fetcher = {
            calls++
            throw IllegalStateException("boom")
        })
        var thrown = false
        try {
            client.videolist(1, 1)
        } catch (e: IllegalStateException) {
            thrown = true
        }
        assertTrue(thrown)
        assertEquals(3, calls)
    }

    @Test
    fun `4xx throws immediately without retry`() = runTest {
        var calls = 0
        val client = AggCmsClient(fetcher = {
            calls++
            throw AggCmsClient.Companion.HttpStatusException(404, "HTTP 404")
        })
        var thrown = false
        try {
            client.videolist(1, 1)
        } catch (e: AggCmsClient.Companion.HttpStatusException) {
            thrown = true
            assertEquals(404, e.status)
        }
        assertTrue(thrown)
        assertEquals(1, calls)
    }

    @Test
    fun `detail parses episodes from all source groups`() = runTest {
        val client = AggCmsClient(fetcher = { detailJson })
        val item = client.detail("src~111")!!
        assertEquals("完整简介", item.content)
        val episodes = client.parseEpisodes(item.playFrom, item.playUrl)
        assertEquals(3, episodes.size)
        assertEquals("国语中字", episodes[0].title)
        assertEquals("https://example.com/a.m3u8", episodes[0].url)
        assertEquals("lzm3u8", episodes[0].source)
        assertEquals("粤语中字", episodes[1].title)
        assertEquals("https://example.com/b.m3u8", episodes[1].url)
        assertEquals("lzm3u8", episodes[1].source)
        assertEquals("国语中字", episodes[2].title)
        assertEquals("https://example.com/c.m3u8", episodes[2].url)
        assertEquals("liangzi", episodes[2].source)
    }

    @Test
    fun `parseEpisodes falls back to generated source names`() {
        val client = AggCmsClient(fetcher = { "" })
        val episodes = client.parseEpisodes(
            "",
            "第1集\$https://example.com/a.m3u8\$\$\$第1集\$https://example.com/b.m3u8",
        )
        assertEquals(2, episodes.size)
        assertEquals("线路 1", episodes[0].source)
        assertEquals("线路 2", episodes[1].source)
    }

    @Test
    fun `cancellation is not retried as a failure`() = runTest {
        var calls = 0
        val client = AggCmsClient(fetcher = {
            calls++
            throw kotlinx.coroutines.CancellationException("gone")
        })
        var cancelled = false
        try {
            client.videolist(1, 1)
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(1, calls)
    }

    @Test
    fun `parseEpisodes handles empty and bare urls`() {
        val client = AggCmsClient(fetcher = { "" })
        assertTrue(client.parseEpisodes("", "").isEmpty())
        val bare = client.parseEpisodes("x", "https://example.com/only.m3u8")
        assertEquals(1, bare.size)
        assertEquals("第 1 集", bare[0].title)
        assertEquals("https://example.com/only.m3u8", bare[0].url)
    }

    @Test
    fun `repository maps categories browse detail and fails soft`() = runTest {
        val client = AggCmsClient(fetcher = { url ->
            if (url.contains("ac=detail")) detailJson else listJson
        })
        val repo = AggSourceRepository(client = client)
        val categories = repo.categories()
        assertEquals(listOf("全部", "电影", "电视剧"), categories.map { it.name })
        assertEquals("all", categories[0].id)
        assertEquals("1", categories[1].id)
        val items = repo.browse("1", 1)
        assertEquals(2, items.size)
        assertEquals("src~111", items[0].id)
        val detail = repo.detail("src~111")!!
        assertEquals("完整简介", detail.description)
        assertEquals(3, detail.episodes.size)
        // Failure path: every call fails -> empty / null, no throw.
        val failing = AggSourceRepository(
            client = AggCmsClient(fetcher = { throw IllegalStateException("down") }),
        )
        assertTrue(failing.categories().isEmpty())
        assertTrue(failing.browse("1", 1).isEmpty())
        assertNull(failing.detail("src~111"))
    }

    @Test
    fun `browsePaged reports hasMore from pagecount`() = runTest {
        val lastPageJson = listJson
            .replace("\"page\":1", "\"page\":700")
        val client = AggCmsClient(fetcher = { url ->
            if (url.contains("pg=700")) lastPageJson else listJson
        })
        val repo = AggSourceRepository(client = client)
        val first = repo.browsePaged("1", 1)
        assertEquals(2, first.items.size)
        assertTrue(first.hasMore)
        val last = repo.browsePaged("1", 700)
        assertEquals(2, last.items.size)
        assertFalse(last.hasMore)
    }

    @Test
    fun `browsePaged fails soft with no more pages`() = runTest {
        val failing = AggSourceRepository(
            client = AggCmsClient(fetcher = { throw IllegalStateException("down") }),
        )
        val page = failing.browsePaged("1", 2)
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun `browsePaged all merges every type instead of returning movies only`() = runTest {
        val client = AggCmsClient(fetcher = { listJson })
        val repo = AggSourceRepository(client = client)
        val page = repo.browsePaged("all", 1)
        // Classes are t=1 and t=2, each returning the same 2 fixture items.
        assertEquals(4, page.items.size)
        assertTrue(page.hasMore)
        // Non-numeric ids behave the same as "all".
        val unknown = repo.browsePaged("movie", 1)
        assertEquals(4, unknown.items.size)
    }

    @Test
    fun `browse delegates to browsePaged items`() = runTest {
        val client = AggCmsClient(fetcher = { listJson })
        val repo = AggSourceRepository(client = client)
        assertEquals(2, repo.browse("1", 1).size)
    }
}
