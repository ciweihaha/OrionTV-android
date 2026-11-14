package com.ciweihaha.oriontv.data.placeholder

import com.ciweihaha.oriontv.data.model.Channel
import com.ciweihaha.oriontv.data.model.Program
import com.ciweihaha.oriontv.data.model.VodCategory
import com.ciweihaha.oriontv.data.model.VodItem

/**
 * Stage-1 demo data ONLY. Every screen that shows it also shows a
 * "演示数据" note. Stage 2 replaces this object with real source parsing;
 * nothing here is presented as real content, and no red-line source
 * (点播首页、T2、BBC、天光云影、TV 5.9.0、凤凰、moontv) appears in it.
 */
object PlaceholderData {

    val channels: List<Channel> = listOf(
        Channel("c1", "001", "央视综合", "央视"),
        Channel("c2", "002", "央视体育", "央视"),
        Channel("c3", "003", "央视电影", "央视"),
        Channel("c4", "004", "湖南卫视", "卫视"),
        Channel("c5", "005", "浙江卫视", "卫视"),
        Channel("c6", "006", "东方卫视", "卫视"),
        Channel("c7", "007", "凤凰资讯", "港澳台"),
        Channel("c8", "008", "HBO", "国际"),
    )

    val categories: List<VodCategory> = listOf(
        VodCategory("all", "全部"),
        VodCategory("movie", "电影"),
        VodCategory("drama", "电视剧"),
        VodCategory("variety", "综艺"),
        VodCategory("anime", "动漫"),
        VodCategory("doc", "纪录片"),
    )

    private val demoTitles = listOf(
        "漫长的季节" to "2023",
        "繁花" to "2024",
        "庆余年" to "2024",
        "唐朝诡事录" to "2022",
        "封神第一部" to "2023",
        "流浪地球 2" to "2023",
        "灌篮高手" to "2022",
        "咒术回战" to "2020",
        "王牌对王牌" to "2024",
        "奔跑吧" to "2024",
        "舌尖上的中国" to "2012",
        "航拍中国" to "2017",
    )

    fun vodItems(categoryId: String): List<VodItem> = demoTitles.mapIndexed { index, (title, year) ->
        VodItem(id = "$categoryId-$index", title = title, year = year, remarks = "演示数据")
    }

    /** Demo programmes anchored on the current hour so now/next always exist. */
    fun programs(channelId: String, fromMs: Long, toMs: Long): List<Program> {
        val hour = 3_600_000L
        val base = (fromMs / hour) * hour
        val slots = listOf("新闻联播", "焦点访谈", "黄金档剧场", "晚间新闻", "今日说法", "天气预报")
        val out = mutableListOf<Program>()
        var t = base
        var i = (channelId.hashCode() and 0x7fffffff) % slots.size
        while (t < toMs) {
            val end = t + hour
            if (end > fromMs) {
                out += Program(slots[i % slots.size], t, end)
            }
            t = end
            i++
        }
        return out
    }

    /** Standard public ExoPlayer test stream — proves Media3 playback end to end. */
    const val TEST_STREAM_URL =
        "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_hevc/master.m3u8"
    const val TEST_STREAM_TITLE = "播放器测试（BipBop 测试流）"
}
