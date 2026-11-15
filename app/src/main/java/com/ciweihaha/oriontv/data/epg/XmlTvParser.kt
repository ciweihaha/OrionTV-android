package com.ciweihaha.oriontv.data.epg

import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * XMLTV parser (pure JVM — javax.xml, no Android APIs — unit-tested).
 *
 * - `<channel id>` + `<display-name>`, `<programme channel start stop>` +
 *   `<title>`; CDATA and XML entities come through `textContent`.
 * - Times are `yyyyMMddHHmmss +0800`, with fallbacks for a missing
 *   timezone and a colon timezone (`+08:00`); illegal values are dropped
 *   and programmes with `end <= start` are discarded.
 * - Matching is by `tvg-id` first, falling back to a normalised name
 *   (whitespace / `-` / `_` stripped, lowercased).
 */
object XmlTvParser {

    data class Programme(
        val channelId: String,
        val title: String,
        val startMs: Long,
        val endMs: Long,
    )

    data class EpgData(
        /** channel id → programmes, sorted by start time. */
        val programmesByChannel: Map<String, List<Programme>>,
        /** channel id → display name. */
        val channelNames: Map<String, String>,
    )

    fun parse(xml: String): EpgData {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        // XXE hardening; each call is best-effort on old parsers.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(InputSource(StringReader(xml)))

        val channelNames = mutableMapOf<String, String>()
        val channels = doc.getElementsByTagName("channel")
        for (i in 0 until channels.length) {
            val el = channels.item(i) as? Element ?: continue
            val id = el.getAttribute("id").trim()
            if (id.isEmpty()) continue
            val nameNodes = el.getElementsByTagName("display-name")
            val name = if (nameNodes.length > 0) nameNodes.item(0).textContent.trim() else id
            channelNames[id] = name
        }

        val programmes = mutableMapOf<String, MutableList<Programme>>()
        val programmeNodes = doc.getElementsByTagName("programme")
        for (i in 0 until programmeNodes.length) {
            val el = programmeNodes.item(i) as? Element ?: continue
            val channel = el.getAttribute("channel").trim()
            val start = parseXmltvTime(el.getAttribute("start")) ?: continue
            val stop = parseXmltvTime(el.getAttribute("stop")) ?: continue
            if (stop <= start) continue
            val titleNodes = el.getElementsByTagName("title")
            val title = if (titleNodes.length > 0) titleNodes.item(0).textContent.trim() else ""
            programmes.getOrPut(channel) { mutableListOf() }
                .add(Programme(channelId = channel, title = title, startMs = start, endMs = stop))
        }
        val sorted = programmes.mapValues { (_, list) -> list.sortedBy { it.startMs } }
        return EpgData(programmesByChannel = sorted, channelNames = channelNames)
    }

    /** XMLTV time: `yyyyMMddHHmmss +0800`, with no-timezone / colon-timezone fallbacks. */
    fun parseXmltvTime(raw: String): Long? {
        val normalized = raw.trim().replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        for (pattern in listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss ZZZ", "yyyyMMddHHmmss")) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = false
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(normalized)?.time
            } catch (_: Exception) {
                // Try the next pattern.
            }
        }
        return null
    }

    /** Normalised name: strip whitespace / `-` / `_`, lowercase. */
    fun normalizeName(name: String): String =
        name.replace(Regex("[\\s\\-_]+"), "").lowercase()

    private fun programmesFor(
        data: EpgData,
        tvgId: String?,
        channelName: String,
    ): List<XmlTvParser.Programme>? {
        val byId = tvgId?.let { data.programmesByChannel[it] }
        if (byId != null) return byId
        return data.programmesByChannel.entries
            .firstOrNull { (channelId, _) ->
                val name = data.channelNames[channelId]
                name != null && normalizeName(name) == normalizeName(channelName)
            }?.value
    }

    fun nowPlaying(
        data: EpgData,
        tvgId: String?,
        channelName: String,
        nowMs: Long,
    ): Programme? =
        programmesFor(data, tvgId, channelName)
            ?.firstOrNull { it.startMs <= nowMs && nowMs < it.endMs }

    fun nextProgramme(
        data: EpgData,
        tvgId: String?,
        channelName: String,
        nowMs: Long,
    ): Programme? =
        programmesFor(data, tvgId, channelName)
            ?.firstOrNull { it.startMs > nowMs }

    fun programmesForChannel(
        data: EpgData,
        tvgId: String?,
        channelName: String,
    ): List<Programme> = programmesFor(data, tvgId, channelName).orEmpty()

    /**
     * Cache freshness: a cached file is only fresh while it still holds a
     * programme that has not ended yet — otherwise a file fetched just
     * after midnight can leave the whole day without programmes.
     */
    fun isCacheFresh(data: EpgData, cachedAtMs: Long, nowMs: Long, ttlMs: Long = 6 * 60 * 60 * 1000L): Boolean {
        if (nowMs - cachedAtMs > ttlMs) return false
        return data.programmesByChannel.values.any { programmes ->
            programmes.any { it.endMs > nowMs }
        }
    }
}
