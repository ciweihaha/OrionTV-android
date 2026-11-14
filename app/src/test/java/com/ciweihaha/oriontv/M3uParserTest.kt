package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.data.source.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private val sample = """
        #EXTM3U
        #EXTINF:-1 tvg-logo="https://example.com/cctv1.png" group-title="央视",CCTV-1 高清
        https://example.com/cctv1.m3u8
        #EXTINF:-1 tvg-logo="https://example.com/cctv5.png" group-title="央视",CCTV-5 体育
        https://example.com/cctv5.m3u8
    """.trimIndent()

    @Test
    fun `parses name logo group and url`() {
        val channels = M3uParser.parse(sample)
        assertEquals(2, channels.size)
        val first = channels[0]
        assertEquals("https://example.com/cctv1.m3u8", first.id)
        assertEquals("001", first.number)
        assertEquals("CCTV-1 高清", first.name)
        assertEquals("央视", first.group)
        assertEquals("https://example.com/cctv1.png", first.logoUrl)
        assertEquals("https://example.com/cctv1.m3u8", first.streamUrl)
        assertEquals("002", channels[1].number)
    }

    @Test
    fun `comma inside quoted group and inside name`() {
        val text = """
            #EXTINF:-1 group-title="A,B",CCTV-1,高清
            https://example.com/a.m3u8
        """.trimIndent()
        val channels = M3uParser.parse(text)
        assertEquals(1, channels.size)
        assertEquals("A,B", channels[0].group)
        assertEquals("CCTV-1,高清", channels[0].name)
    }

    @Test
    fun `duplicate url keeps first entry only`() {
        val text = """
            #EXTINF:-1 group-title="央视",CCTV-1
            https://example.com/same.m3u8
            #EXTINF:-1 group-title="卫视",湖南卫视
            https://example.com/same.m3u8
        """.trimIndent()
        val channels = M3uParser.parse(text)
        assertEquals(1, channels.size)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("001", channels[0].number)
    }

    @Test
    fun `extinf without comma yields unknown name`() {
        val text = """
            #EXTINF:-1 tvg-logo="https://example.com/x.png"
            https://example.com/x.m3u8
        """.trimIndent()
        val channels = M3uParser.parse(text)
        assertEquals(1, channels.size)
        assertEquals("Unknown", channels[0].name)
    }

    @Test
    fun `url without preceding extinf is skipped`() {
        val text = """
            https://example.com/orphan.m3u8
            #EXTINF:-1 group-title="央视",CCTV-1
            https://example.com/cctv1.m3u8
        """.trimIndent()
        val channels = M3uParser.parse(text)
        assertEquals(1, channels.size)
        assertEquals("CCTV-1", channels[0].name)
    }

    @Test
    fun `missing logo and group use defaults`() {
        val text = """
            #EXTINF:-1,CCTV-1
            https://example.com/cctv1.m3u8
        """.trimIndent()
        val channels = M3uParser.parse(text)
        assertEquals(1, channels.size)
        assertNull(channels[0].logoUrl)
        assertEquals("Default", channels[0].group)
    }

    @Test
    fun `extinf without following url is dropped`() {
        val text = """
            #EXTINF:-1 group-title="央视",CCTV-1
        """.trimIndent()
        assertTrue(M3uParser.parse(text).isEmpty())
    }

    @Test
    fun `other directives and blank lines are ignored`() {
        val text = """
            #EXTM3U x-tvg-url="https://example.com/epg.xml"
            #EXTGRP:央视

            #EXTINF:-1,CCTV-1
            https://example.com/cctv1.m3u8
            #EXTVLCOPT:network-caching=1000
        """.trimIndent()
        val channels = M3uParser.parse(text)
        assertEquals(1, channels.size)
        assertEquals("CCTV-1", channels[0].name)
    }
}
