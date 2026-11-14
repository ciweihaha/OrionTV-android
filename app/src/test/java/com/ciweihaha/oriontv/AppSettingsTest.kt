package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.data.settings.normalizeM3uUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `blank input is null`() {
        assertNull(normalizeM3uUrl(""))
        assertNull(normalizeM3uUrl("   "))
    }

    @Test
    fun `missing scheme defaults to https`() {
        assertEquals(
            "https://example.com/list.m3u",
            normalizeM3uUrl("example.com/list.m3u"),
        )
    }

    @Test
    fun `http and https pass through`() {
        assertEquals(
            "http://example.com/list.m3u",
            normalizeM3uUrl("http://example.com/list.m3u"),
        )
        assertEquals(
            "https://example.com/list.m3u",
            normalizeM3uUrl("https://example.com/list.m3u"),
        )
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(
            "https://example.com/list.m3u",
            normalizeM3uUrl("  https://example.com/list.m3u  "),
        )
    }

    @Test
    fun `non-http schemes are rejected`() {
        assertNull(normalizeM3uUrl("ftp://example.com/list.m3u"))
    }

    @Test
    fun `missing host is rejected`() {
        assertNull(normalizeM3uUrl("https://"))
        assertNull(normalizeM3uUrl("https://?x=1"))
    }

    @Test
    fun `unparseable input is rejected`() {
        assertNull(normalizeM3uUrl("not a url"))
    }
}
