package com.ciweihaha.oriontv

import androidx.media3.common.PlaybackException
import com.ciweihaha.oriontv.ui.player.PlayerActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R14-A: the player retry policy is pure logic — transient IO errors
 * (2004/2001/2002) retry with backoff, everything else fails at once.
 * The policy is tested here; the wiring (Handler delay, re-prepare,
 * line switch) is exercised by CI / on-device.
 */
class PlayerRetryPolicyTest {

    @Test
    fun `transient IO errors retry while under the cap`() {
        assertTrue(
            PlayerActivity.shouldRetryIoError(
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 0
            ),
        )
        assertTrue(
            PlayerActivity.shouldRetryIoError(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, 2
            ),
        )
        assertTrue(
            PlayerActivity.shouldRetryIoError(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlayerActivity.MAX_IO_ERROR_RETRIES - 1,
            ),
        )
    }

    @Test
    fun `no retry once the cap is reached`() {
        assertFalse(
            PlayerActivity.shouldRetryIoError(
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlayerActivity.MAX_IO_ERROR_RETRIES,
            ),
        )
        assertFalse(
            PlayerActivity.shouldRetryIoError(
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlayerActivity.MAX_IO_ERROR_RETRIES + 1,
            ),
        )
    }

    @Test
    fun `non-transient errors never retry`() {
        assertFalse(
            PlayerActivity.shouldRetryIoError(
                PlaybackException.ERROR_CODE_DECODING_FAILED, 0
            ),
        )
        assertFalse(
            PlayerActivity.shouldRetryIoError(
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, 0
            ),
        )
        assertFalse(
            PlayerActivity.shouldRetryIoError(
                PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED, 0
            ),
        )
        assertFalse(PlayerActivity.shouldRetryIoError(-1, 0))
    }

    @Test
    fun `backoff grows then caps at 8s`() {
        assertEquals(1_000L, PlayerActivity.retryDelayMs(1))
        assertEquals(2_000L, PlayerActivity.retryDelayMs(2))
        assertEquals(3_000L, PlayerActivity.retryDelayMs(3))
        assertEquals(5_000L, PlayerActivity.retryDelayMs(4))
        assertEquals(8_000L, PlayerActivity.retryDelayMs(5))
        assertEquals(8_000L, PlayerActivity.retryDelayMs(99))
    }

    @Test
    fun `first error-driven switch is allowed with two lines`() {
        // Fresh episode: no prior error-driven switch.
        assertTrue(PlayerActivity.shouldAutoSwitchLine(2, 0, 0L))
        assertTrue(PlayerActivity.shouldAutoSwitchLine(2, 0, 5_000L))
    }

    @Test
    fun `second error-driven switch is blocked inside one unstable episode`() {
        // R15-D regression: R14's READY-reset flag allowed this — the
        // new line became READY, the flag cleared, and two flaky lines
        // ping-ponged forever. A line that only played 5s is unstable,
        // so a prior switch in this episode blocks the next one.
        assertFalse(PlayerActivity.shouldAutoSwitchLine(2, 1, 5_000L))
        assertFalse(PlayerActivity.shouldAutoSwitchLine(2, 1, 0L))
        assertFalse(PlayerActivity.shouldAutoSwitchLine(3, 2, 30_000L))
    }

    @Test
    fun `stable playback starts a new episode and allows switching again`() {
        // The new line played 60s+ before failing: a new, unrelated
        // failure, so the previous switch does not count.
        assertTrue(
            PlayerActivity.shouldAutoSwitchLine(
                2, 1, PlayerActivity.STABLE_PLAY_MS
            ),
        )
        assertTrue(
            PlayerActivity.shouldAutoSwitchLine(
                2, 1, PlayerActivity.STABLE_PLAY_MS + 60_000L
            ),
        )
    }

    @Test
    fun `single line never auto-switches`() {
        assertFalse(PlayerActivity.shouldAutoSwitchLine(1, 0, 0L))
        assertFalse(PlayerActivity.shouldAutoSwitchLine(1, 0, 120_000L))
        assertFalse(PlayerActivity.shouldAutoSwitchLine(0, 0, 0L))
    }
}
