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
}
