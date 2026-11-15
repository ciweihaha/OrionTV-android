package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.data.epg.XmlTvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlTvParserTest {

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="cctv1"><display-name>CCTV-1 综合</display-name></channel>
          <channel id="cctv2"><display-name>CCTV-2 财经</display-name></channel>
          <programme channel="cctv1" start="20261114190000 +0800" stop="20261114200000 +0800">
            <title>新闻联播</title>
          </programme>
          <programme channel="cctv1" start="20261114200000 +0800" stop="20261114210000 +0800">
            <title>焦点访谈</title>
          </programme>
          <programme channel="cctv1" start="20261114210000 +0800" stop="20261114200000 +0800">
            <title>坏节目（结束早于开始，应丢弃）</title>
          </programme>
          <programme channel="cctv2" start="20261114190000 +0800" stop="20261114200000 +0800">
            <title><![CDATA[经济半小时]]></title>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun parse_readsChannelsAndProgrammes() {
        val data = XmlTvParser.parse(xml)
        assertEquals("CCTV-1 综合", data.channelNames["cctv1"])
        assertEquals(2, data.programmesByChannel["cctv1"]?.size)
        assertEquals("新闻联播", data.programmesByChannel["cctv1"]?.get(0)?.title)
        assertEquals("焦点访谈", data.programmesByChannel["cctv1"]?.get(1)?.title)
        // CDATA title comes through textContent.
        assertEquals("经济半小时", data.programmesByChannel["cctv2"]?.get(0)?.title)
    }

    @Test
    fun parseXmltvTime_handlesTimezoneVariants() {
        val withZone = XmlTvParser.parseXmltvTime("20261114190000 +0800")
        val withoutZone = XmlTvParser.parseXmltvTime("20261114190000")
        val colonZone = XmlTvParser.parseXmltvTime("20261114190000 +08:00")
        assertEquals(withZone, colonZone)
        // No timezone is read as UTC, so it is 8h later than the +0800 value.
        assertEquals(withZone?.plus(8 * 3_600_000L), withoutZone)
        assertNull(XmlTvParser.parseXmltvTime("not a time"))
        assertNull(XmlTvParser.parseXmltvTime(""))
    }

    @Test
    fun nowAndNext_matchByTvgId() {
        val data = XmlTvParser.parse(xml)
        // 19:30 +0800 on 2026-11-14.
        val now = XmlTvParser.parseXmltvTime("20261114193000 +0800")!!
        assertEquals("新闻联播", XmlTvParser.nowPlaying(data, "cctv1", "CCTV-1", now)?.title)
        assertEquals("焦点访谈", XmlTvParser.nextProgramme(data, "cctv1", "CCTV-1", now)?.title)
    }

    @Test
    fun nowAndNext_fallBackToNormalisedName() {
        val data = XmlTvParser.parse(xml)
        val now = XmlTvParser.parseXmltvTime("20261114193000 +0800")!!
        // Unknown tvg-id, but the name matches after normalisation.
        assertEquals(
            "经济半小时",
            XmlTvParser.nowPlaying(data, "no-such-id", "CCTV-2财经", now)?.title,
        )
        assertNull(XmlTvParser.nowPlaying(data, "no-such-id", "不存在的台", now))
    }

    @Test
    fun normalizeName_stripsSeparatorsAndCase() {
        assertEquals("cctv1综合", XmlTvParser.normalizeName("CCTV-1 综合"))
        assertEquals("cctv1综合", XmlTvParser.normalizeName("cctv1综合"))
    }

    @Test
    fun isCacheFresh_requiresAFutureProgramme() {
        val data = XmlTvParser.parse(xml)
        val now = XmlTvParser.parseXmltvTime("20261114193000 +0800")!!
        assertTrue(XmlTvParser.isCacheFresh(data, now - 60_000L, now))
        // Cached 7h ago: over the 6h TTL.
        assertTrue(!XmlTvParser.isCacheFresh(data, now - 7 * 3_600_000L, now))
        // All programmes ended: stale even if cached recently.
        val late = XmlTvParser.parseXmltvTime("20261115000000 +0800")!!
        assertTrue(!XmlTvParser.isCacheFresh(data, late - 60_000L, late))
    }
}
