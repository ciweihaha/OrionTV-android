package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.data.model.Channel
import com.ciweihaha.oriontv.data.source.LiveLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLinesTest {

    private fun channel(name: String, url: String?) = Channel(
        id = url ?: name,
        number = "001",
        name = name,
        group = "央视",
        streamUrl = url,
    )

    private val playlist = listOf(
        channel("CCTV-1", "https://example.com/cctv1-a.m3u8"),
        channel("CCTV-1", "https://example.com/cctv1-b.m3u8"),
        channel("湖南卫视", "https://example.com/hunan.m3u8"),
        channel("CCTV-1", "https://example.com/cctv1-c.m3u8"),
        channel("浙江卫视", null),
    )

    @Test
    fun `linesFor groups same-name channels in playlist order`() {
        val lines = LiveLines.linesFor(playlist, playlist[0])
        assertEquals(
            listOf(
                "https://example.com/cctv1-a.m3u8",
                "https://example.com/cctv1-b.m3u8",
                "https://example.com/cctv1-c.m3u8",
            ),
            lines.map { it.streamUrl },
        )
    }

    @Test
    fun `linesFor returns only the channel itself when it has no alternate lines`() {
        val lines = LiveLines.linesFor(playlist, playlist[2])
        assertEquals(1, lines.size)
        assertEquals("https://example.com/hunan.m3u8", lines[0].streamUrl)
    }

    @Test
    fun `linesFor skips unplayable entries`() {
        val lines = LiveLines.linesFor(playlist, playlist[4])
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `indexOf finds the selected line by url`() {
        val lines = LiveLines.linesFor(playlist, playlist[0])
        assertEquals(0, LiveLines.indexOf(lines, playlist[0]))
        assertEquals(1, LiveLines.indexOf(lines, playlist[1]))
        assertEquals(2, LiveLines.indexOf(lines, playlist[3]))
    }

    @Test
    fun `indexOf returns minus one when the channel is not among the lines`() {
        val lines = LiveLines.linesFor(playlist, playlist[0])
        val absent = channel("湖南卫视", "https://example.com/hunan.m3u8")
        assertEquals(-1, LiveLines.indexOf(lines, absent))
        assertEquals(-1, LiveLines.indexOf(emptyList(), playlist[0]))
    }

    @Test
    fun `linesFor matches names ignoring spaces and case`() {
        val mixed = listOf(
            channel("CCTV-1 高清", "https://example.com/a.m3u8"),
            channel("cctv-1高清", "https://example.com/b.m3u8"),
            channel("CCTV-1", "https://example.com/c.m3u8"),
        )
        val lines = LiveLines.linesFor(mixed, mixed[0])
        assertEquals(
            listOf("https://example.com/a.m3u8", "https://example.com/b.m3u8"),
            lines.map { it.streamUrl },
        )
    }

    @Test
    fun `nextIndex wraps around to the first line`() {
        assertEquals(1, LiveLines.nextIndex(0, 3))
        assertEquals(2, LiveLines.nextIndex(1, 3))
        assertEquals(0, LiveLines.nextIndex(2, 3))
        assertEquals(0, LiveLines.nextIndex(0, 1))
        assertEquals(0, LiveLines.nextIndex(0, 0))
    }
}
