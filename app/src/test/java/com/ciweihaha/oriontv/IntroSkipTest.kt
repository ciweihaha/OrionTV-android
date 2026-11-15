package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.ui.player.IntroSkip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroSkipTest {

    @Test
    fun `learned intro wins over default seconds`() {
        assertEquals(120_000L, IntroSkip.resolveIntroEndMs(120_000L, 90))
        assertEquals(120_000L, IntroSkip.resolveIntroEndMs(120_000L, 0))
    }

    @Test
    fun `no learned value falls back to configured seconds`() {
        assertEquals(90_000L, IntroSkip.resolveIntroEndMs(null, 90))
        assertEquals(30_000L, IntroSkip.resolveIntroEndMs(null, 30))
    }

    @Test
    fun `no learned value and zero seconds means no skip`() {
        assertEquals(0L, IntroSkip.resolveIntroEndMs(null, 0))
        assertEquals(0L, IntroSkip.resolveIntroEndMs(-5L, 0))
    }

    @Test
    fun `learn intro from manual forward seek inside window`() {
        // 片头内起跳、越过认定结束点、大跳跃 → 学
        assertTrue(IntroSkip.shouldLearnIntroEnd(0L, 120_000L, 90_000L, false))
        assertTrue(IntroSkip.shouldLearnIntroEnd(45_000L, 150_000L, 90_000L, false))
        // 片长已知、落点离片尾够远 → 学
        assertTrue(IntroSkip.shouldLearnIntroEnd(0L, 120_000L, 90_000L, false, durationMs = 1_800_000L))
    }

    @Test
    fun `do not learn seeks starting past the intro`() {
        // 起点已过认定结束点：正片中途的场景快进，学了会把下集正片开头也跳掉
        assertFalse(IntroSkip.shouldLearnIntroEnd(100_000L, 200_000L, 90_000L, false))
        assertFalse(IntroSkip.shouldLearnIntroEnd(90_000L, 100_000L, 90_000L, false))
    }

    @Test
    fun `do not learn skip-button seeks and small nudges`() {
        // 落点等于认定结束点：跳过按钮点的就是这个点，不是用户行为
        assertFalse(IntroSkip.shouldLearnIntroEnd(0L, 90_000L, 90_000L, false))
        // 落点没越过认定结束点
        assertFalse(IntroSkip.shouldLearnIntroEnd(0L, 60_000L, 90_000L, false))
        // 跳跃 <30 秒：误触的单次 +10 秒
        assertFalse(IntroSkip.shouldLearnIntroEnd(80_000L, 95_000L, 90_000L, false))
        // 没认定过片头结束点（时长 0 = 不跳）：无从学起
        assertFalse(IntroSkip.shouldLearnIntroEnd(0L, 120_000L, 0L, false))
    }

    @Test
    fun `do not learn jumps past the intro or into the credits`() {
        // 落点超过 10 分钟：跳到正片中间去了
        assertFalse(IntroSkip.shouldLearnIntroEnd(0L, 700_000L, 90_000L, false))
        // 落点距片尾不到 10 秒：跳到片尾去了
        assertFalse(IntroSkip.shouldLearnIntroEnd(0L, 195_000L, 90_000L, false, durationMs = 200_000L))
        // 直播（片长未知）时片尾守卫不拦，但 10 分钟上限仍拦
        assertTrue(IntroSkip.shouldLearnIntroEnd(0L, 120_000L, 90_000L, false, durationMs = 0L))
    }
}
