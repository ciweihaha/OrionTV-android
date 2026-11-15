package com.ciweihaha.oriontv.ui.player

/**
 * 片头结束点纯逻辑（与原生版 IntroSkip 同款规则）：
 * - 认定值：学到的落点优先（按 vodId 存，本剧通用），没有学过用设置里的
 *   默认片头时长兜底，0 表示不跳（不显示跳过按钮）；
 * - 学习式片头：agg 接口不给片头时间、HLS 流里也没有章节信息，真正的片头
 *   只能从用户的行为里学——用户在片头窗口内手动往前跳，落点大概率就是
 *   这部剧的片头结束点。学到的落点存 LibraryStore，下集起直接用它。
 *   安卓版没有手动标记按钮，所以没有“显式标记优先”这一档：最近一次符合
 *   条件的跳跃就是最新的认定值（hasMark 恒为 false，见 shouldLearnIntroEnd
 *   的参数说明）；想恢复默认时长请在设置里改秒数。
 */
object IntroSkip {

    /** 默认片头时长（秒），与原生版 INTRO_SKIP_SECONDS 默认值一致。 */
    const val DEFAULT_SKIP_SECONDS = 90

    /** 学习式片头：一次手动跳跃至少这么大才算“跳片头”，过滤误触的单次 +10 秒。 */
    const val MIN_LEARN_JUMP_MS = 30_000L

    /** 学习式片头：落点超过这个值就不是片头了（用户跳到正片中间去了）。 */
    const val MAX_LEARNABLE_INTRO_MS = 600_000L

    /** 自动跳/学习时片尾要留的余量（与 WatchRecord  finished margin 同量级）。 */
    const val MIN_REMAINING_MS = 10_000L

    /** 算出本集真正用来跳过的片头结束时间（毫秒），没得跳时为 0。 */
    fun resolveIntroEndMs(learnedMs: Long?, skipSeconds: Int): Long {
        if (learnedMs != null && learnedMs > 0) return learnedMs
        if (skipSeconds > 0) return skipSeconds * 1000L
        return 0L
    }

    /**
     * 这次手动 seek 是否值得学成片头结束点（可单测）。
     * 只认“从片头内起跳、越过当前认定结束点”的大跳跃：
     * - 起点已在认定结束点之外不学——那是正片中途的场景快进，学了会把下集
     *   的正片开头也跳掉；
     * - 落点等于认定结束点不学——跳过按钮点的就是这个点，不是用户行为；
     * - 跳跃太小（<30 秒）不学，过滤误触的单次 +10 秒；
     * - 落点超过 10 分钟不学，那不是片头，是跳到正片中间去了；
     * - 片长已知时，落点距片尾不到 10 秒不学。
     * @param hasMark 安卓版没有手动标记按钮，调用方恒传 false（见类注释）；
     *   保留这个参数是为了和原生版同款规则、测试可以平行对照。
     * @param durationMs 片长（毫秒）；<=0 表示未知（直播），不做片尾守卫。
     */
    fun shouldLearnIntroEnd(
        fromMs: Long,
        toMs: Long,
        introEndMs: Long,
        hasMark: Boolean,
        durationMs: Long = 0L,
    ): Boolean {
        if (hasMark) return false
        if (introEndMs <= 0) return false
        if (fromMs < 0 || fromMs >= introEndMs) return false
        if (toMs <= introEndMs) return false
        if (toMs - fromMs < MIN_LEARN_JUMP_MS) return false
        if (toMs > MAX_LEARNABLE_INTRO_MS) return false
        if (durationMs > 0 && toMs >= durationMs - MIN_REMAINING_MS) return false
        return true
    }
}
