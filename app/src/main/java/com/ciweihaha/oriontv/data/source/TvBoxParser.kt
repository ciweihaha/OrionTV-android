package com.ciweihaha.oriontv.data.source

import com.ciweihaha.oriontv.data.model.Episode
import com.ciweihaha.oriontv.data.model.VodCategory
import com.ciweihaha.oriontv.data.model.VodDetail
import com.ciweihaha.oriontv.data.model.VodItem
import org.json.JSONObject

/**
 * Pure parsers for the TVBox-compatible CMS JSON API (type 1 sources,
 * AppleCMS-style endpoints). No Android APIs and no I/O here, so every
 * function is unit-tested with plain JUnit.
 *
 * Endpoints (relative to the configured API base URL):
 * - categories: `?ac=list` (fallback `?ac=class`)
 * - list/search: `?ac=videolist&t=<type_id>&pg=<page>` / `?ac=videolist&wd=<keyword>&pg=<page>`
 * - detail: `?ac=detail&ids=<vod_id>`
 *
 * Play URLs: `vod_play_from` holds source names separated by `$$$`,
 * `vod_play_url` holds one episode list per source, also `$$$`-separated;
 * inside a list episodes are `#`-separated `name$url` pairs.
 */
object TvBoxParser {

    /** `{"class":[{"type_id":"1","type_name":"电影"}, ...]}` */
    fun parseCategories(json: String): List<VodCategory> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("class") ?: return emptyList()
        val out = mutableListOf<VodCategory>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("type_id").trim()
            val name = obj.optString("type_name").trim()
            if (id.isNotEmpty() && name.isNotEmpty()) {
                out += VodCategory(id, name)
            }
        }
        return out
    }

    /** `{"list":[{"vod_id","vod_name","vod_pic","vod_year","vod_remarks", ...}]}` */
    fun parseVodList(json: String): List<VodItem> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("list") ?: return emptyList()
        val out = mutableListOf<VodItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out += vodItemFromJson(obj)
        }
        return out
    }

    /** `{"list":[{...one vod with vod_play_from / vod_play_url / vod_content...}]}` */
    fun parseDetail(json: String): VodDetail? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val array = root.optJSONArray("list") ?: return null
        if (array.length() == 0) return null
        val obj = array.optJSONObject(0) ?: return null
        val item = vodItemFromJson(obj)
        val description = obj.optString("vod_content").trim().ifEmpty { null }
        val sources = parsePlaySources(
            obj.optString("vod_play_from"),
            obj.optString("vod_play_url"),
        )
        val episodes = flattenEpisodes(sources)
        return VodDetail(item = item, description = description, episodes = episodes)
    }

    /**
     * One episode list per play source. A bare URL with no `$` is a single
     * episode named after the source (or "正片"). Empty/invalid entries are
     * dropped, never crash the parse.
     */
    fun parsePlaySources(playFrom: String, playUrl: String): List<Pair<String, List<Episode>>> {
        if (playUrl.isBlank()) return emptyList()
        val fromNames = playFrom.split("$$$").map { it.trim() }
        val urlGroups = playUrl.split("$$$")
        val out = mutableListOf<Pair<String, List<Episode>>>()
        for ((groupIndex, group) in urlGroups.withIndex()) {
            val sourceName = fromNames.getOrNull(groupIndex).orEmpty()
                .ifEmpty { if (urlGroups.size > 1) "线路${groupIndex + 1}" else "" }
            val episodes = mutableListOf<Episode>()
            for (part in group.split("#")) {
                val trimmed = part.trim()
                if (trimmed.isEmpty()) continue
                val dollar = trimmed.indexOf('$')
                if (dollar < 0) {
                    // Bare URL: single episode.
                    if (trimmed.startsWith("http")) {
                        episodes += Episode(episodes.size + 1, "正片", trimmed)
                    }
                    continue
                }
                val name = trimmed.substring(0, dollar).trim()
                val url = trimmed.substring(dollar + 1).trim()
                if (url.isEmpty()) continue
                episodes += Episode(
                    episodes.size + 1,
                    name.ifEmpty { "第 ${episodes.size + 1} 集" },
                    url,
                )
            }
            if (episodes.isNotEmpty()) out += sourceName to episodes
        }
        return out
    }

    /**
     * Flatten multi-source episodes into one list. With a single source the
     * episode titles are untouched; with several, each title is prefixed
     * with its source name so the grid stays unambiguous.
     */
    fun flattenEpisodes(sources: List<Pair<String, List<Episode>>>): List<Episode> {
        if (sources.size <= 1) return sources.firstOrNull()?.second.orEmpty()
        val out = mutableListOf<Episode>()
        for ((sourceName, episodes) in sources) {
            for (episode in episodes) {
                val title = if (sourceName.isEmpty()) episode.title else "$sourceName ${episode.title}"
                out += episode.copy(title = title)
            }
        }
        return out
    }

    private fun vodItemFromJson(obj: JSONObject): VodItem {
        val id = obj.optString("vod_id").trim()
        val title = obj.optString("vod_name").trim().ifEmpty { "未知标题" }
        val pic = obj.optString("vod_pic").trim().ifEmpty { null }
        val year = obj.optString("vod_year").trim().ifEmpty { null }
        val remarks = obj.optString("vod_remarks").trim().ifEmpty { null }
        return VodItem(id = id, title = title, posterUrl = pic, year = year, remarks = remarks)
    }
}
