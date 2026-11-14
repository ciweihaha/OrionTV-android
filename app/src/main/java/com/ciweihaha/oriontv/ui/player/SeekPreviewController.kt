package com.ciweihaha.oriontv.ui.player

/**
 * Seek-preview state machine (T6/T7). Pure logic, no Android dependency,
 * so it is unit-testable.
 *
 * Behaviour: while the control bar is visible, DPAD left/right moves a
 * *pending* target in 10-second steps and shows a preview bubble with the
 * direction and the landing time. Nothing seeks yet. DPAD_CENTER commits
 * the jump; BACK (or a second press of the opposite key past the origin)
 * cancels. The preview auto-commits after [AUTO_COMMIT_MS] of no input so
 * the player never looks stuck.
 */
class SeekPreviewController(private val durationMs: Long) {

    var pendingTargetMs: Long? = null
        private set

    val hasPending: Boolean get() = pendingTargetMs != null

    /** Move the pending target one 10s step. Returns the new target. */
    fun nudge(currentMs: Long, direction: Int): Long {
        val base = pendingTargetMs ?: currentMs
        val target = (base + direction * STEP_MS).coerceIn(0L, durationMs)
        pendingTargetMs = target
        return target
    }

    /** Commit the pending target (null when nothing is pending). */
    fun commit(): Long? {
        val target = pendingTargetMs
        pendingTargetMs = null
        return target
    }

    fun cancel() {
        pendingTargetMs = null
    }

    companion object {
        const val STEP_MS = 10_000L
        const val AUTO_COMMIT_MS = 2_000L
    }
}
