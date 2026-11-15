package com.ciweihaha.oriontv.data.source

import com.ciweihaha.oriontv.data.model.Channel

/**
 * M3U playlist parser (TVBox-compatible live source, stage 2).
 *
 * Pure logic, no Android dependency, so it is unit-testable. Semantics
 * mirror the React Native reference (`services/m3u.ts#parseM3U`) exactly:
 *
 * - A channel starts at `#EXTINF:` and ends at the next non-`#` line that
 *   contains `://` (the stream URL). The directive match is
 *   case-insensitive: some playlists write `#extinf:` and those channels
 *   used to be dropped entirely.
 * - The name is the text after the first comma that is NOT inside double
 *   quotes (attribute values and names may themselves contain commas,
 *   e.g. `group-title="A,B"` or `CCTV-1,高清`).
 * - `tvg-logo` / `group-title` attributes are read case-insensitively.
 * - `tvg-id` is read the same way (round 1) so the EPG lookup can match
 *   channels by id; missing `tvg-id` yields a null [Channel.tvgId].
 * - A stream URL seen twice keeps a single entry, but every distinct
 *   name it appeared under is merged into that entry ("CCTV-1 /
 *   CCTV-1 高清") instead of silently dropping the later names.
 * - Missing name → "Unknown", missing group → "Default", missing logo → null.
 * - `#EXTINF:` without a comma yields an empty name (→ "Unknown").
 * - A URL line with no preceding `#EXTINF:` is skipped.
 *
 * Channel numbers are assigned sequentially ("001", "002", …) in playlist
 * order; `id` is the stream URL, as in the reference.
 */
object M3uParser {

    /** M3U playlist size limit, same as the reference (R13). */
    const val MAX_BYTES = 5 * 1024 * 1024

    private val LOGO_REGEX = Regex("tvg-logo=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val GROUP_REGEX = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val TVG_ID_REGEX = Regex("tvg-id=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val seenUrls = HashMap<String, Int>()
        var pending: PendingChannel? = null

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                pending = parseExtinf(line)
            } else if (pending != null && line.isNotEmpty() &&
                !line.startsWith("#") && line.contains("://")
            ) {
                val url = line
                val name = pending.name.ifEmpty { "Unknown" }
                val existingIndex = seenUrls[url]
                if (existingIndex == null) {
                    seenUrls[url] = channels.size
                    channels += Channel(
                        id = url,
                        number = "%03d".format(channels.size + 1),
                        name = name,
                        group = pending.group.ifEmpty { "Default" },
                        logoUrl = pending.logo.takeIf { it.isNotEmpty() },
                        streamUrl = url,
                        tvgId = pending.tvgId.takeIf { it.isNotEmpty() },
                    )
                } else {
                    // Same stream under another name: keep every name so
                    // the entry still represents all of them.
                    val existing = channels[existingIndex]
                    if (name != existing.name && name !in existing.name.split(" / ")) {
                        channels[existingIndex] =
                            existing.copy(name = "${existing.name} / $name")
                    }
                }
                pending = null
            }
            // Any other line (other # directives, blanks) is ignored and
            // does not reset a pending #EXTINF:.
        }
        return channels
    }

    private data class PendingChannel(
        val name: String,
        val logo: String,
        val group: String,
        val tvgId: String,
    )

    private fun parseExtinf(line: String): PendingChannel {
        val commaIndex = firstCommaOutsideQuotes(line, startIndex = 8)
        if (commaIndex == -1) {
            return PendingChannel(name = "", logo = "", group = "", tvgId = "")
        }
        val name = line.substring(commaIndex + 1).trim()
        val attrs = line.substring(8, commaIndex)
        val logo = LOGO_REGEX.find(attrs)?.groupValues?.getOrNull(1).orEmpty()
        val group = GROUP_REGEX.find(attrs)?.groupValues?.getOrNull(1).orEmpty()
        val tvgId = TVG_ID_REGEX.find(attrs)?.groupValues?.getOrNull(1).orEmpty()
        return PendingChannel(name = name, logo = logo, group = group, tvgId = tvgId)
    }

    /** Index of the first ',' at or after [startIndex] that is outside quotes, or -1. */
    private fun firstCommaOutsideQuotes(line: String, startIndex: Int): Int {
        var inQuotes = false
        for (i in startIndex until line.length) {
            val ch = line[i]
            if (ch == '"') inQuotes = !inQuotes
            else if (ch == ',' && !inQuotes) return i
        }
        return -1
    }
}
