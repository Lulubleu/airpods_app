package com.airpodsapp.protocol

/**
 * Ear-detection state machine: decides when to pause or resume playback,
 * like iOS does when an AirPod is taken out of the ear.
 *
 * - Fewer buds in ear than before while playing → [Action.PAUSE].
 * - Back to at least as many buds as before the pause → [Action.RESUME],
 *   only if we paused it.
 */
class AutoPause {
    enum class Action { NONE, PAUSE, RESUME }

    private var lastInEar: Int? = null
    private var pausedByUs = false
    private var inEarBeforePause = 0

    fun onStatus(status: PodsStatus, isPlaying: Boolean): Action {
        // Single headsets (AirPods Max, Beats) have no in-ear bits in the beacon.
        if (status !is PodsStatus.Stereo) return Action.NONE

        val inEar = listOf(status.left, status.right).count { it.isInEar }
        val previous = lastInEar
        lastInEar = inEar
        if (previous == null || inEar == previous) return Action.NONE

        if (inEar < previous) {
            if (!isPlaying) return Action.NONE
            if (!pausedByUs) inEarBeforePause = previous
            pausedByUs = true
            return Action.PAUSE
        }

        // inEar > previous. isPlaying is not checked here: Android keeps reporting music as
        // active for a few seconds after a pause, and resuming with MEDIA_PLAY is harmless
        // if the user already restarted playback.
        if (!pausedByUs) return Action.NONE
        if (inEar >= inEarBeforePause) {
            pausedByUs = false
            return Action.RESUME
        }
        return Action.NONE
    }

    fun reset() {
        lastInEar = null
        pausedByUs = false
        inEarBeforePause = 0
    }
}
