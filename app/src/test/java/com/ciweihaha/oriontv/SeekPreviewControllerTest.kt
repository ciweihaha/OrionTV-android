package com.ciweihaha.oriontv

import com.ciweihaha.oriontv.ui.player.SeekPreviewController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekPreviewControllerTest {

    private val duration = 600_000L // 10 minutes

    @Test
    fun `nudge moves in 10s steps from current position`() {
        val controller = SeekPreviewController(duration)
        assertEquals(70_000L, controller.nudge(60_000L, 1))
        assertEquals(80_000L, controller.nudge(60_000L, 1))
        assertEquals(70_000L, controller.nudge(60_000L, -1))
        assertTrue(controller.hasPending)
    }

    @Test
    fun `nudge clamps at zero and at duration`() {
        val controller = SeekPreviewController(duration)
        assertEquals(0L, controller.nudge(5_000L, -1))
        val atEnd = SeekPreviewController(duration)
        assertEquals(duration, atEnd.nudge(duration - 5_000L, 1))
        assertEquals(duration, atEnd.nudge(duration - 5_000L, 1))
    }

    @Test
    fun `commit returns target and clears pending`() {
        val controller = SeekPreviewController(duration)
        controller.nudge(60_000L, 1)
        assertEquals(70_000L, controller.commit())
        assertFalse(controller.hasPending)
        assertNull(controller.commit())
    }

    @Test
    fun `cancel clears pending without seeking`() {
        val controller = SeekPreviewController(duration)
        controller.nudge(60_000L, 1)
        controller.cancel()
        assertFalse(controller.hasPending)
        assertNull(controller.commit())
    }
}
