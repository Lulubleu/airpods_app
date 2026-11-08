package com.airpodsapp.protocol

import com.airpodsapp.protocol.AutoPause.Action
import kotlin.test.Test
import kotlin.test.assertEquals

class AutoPauseTest {
    private fun stereo(left: Boolean, right: Boolean) = PodsStatus.Stereo(
        model = PodsModel.AIRPODS_2,
        left = PodComponent(9, isCharging = false, isInEar = left),
        right = PodComponent(9, isCharging = false, isInEar = right),
        case = PodComponent(15, isCharging = false),
    )

    private val both = stereo(left = true, right = true)
    private val one = stereo(left = true, right = false)
    private val none = stereo(left = false, right = false)

    @Test
    fun `pauses when one bud is removed and resumes when it is back`() {
        val auto = AutoPause()
        assertEquals(Action.NONE, auto.onStatus(both, isPlaying = true))
        assertEquals(Action.PAUSE, auto.onStatus(one, isPlaying = true))
        assertEquals(Action.RESUME, auto.onStatus(both, isPlaying = false))
    }

    @Test
    fun `does nothing when nothing is playing`() {
        val auto = AutoPause()
        auto.onStatus(both, isPlaying = false)
        assertEquals(Action.NONE, auto.onStatus(one, isPlaying = false))
        assertEquals(Action.NONE, auto.onStatus(both, isPlaying = false))
    }

    @Test
    fun `resumes only once both buds are back after removing both`() {
        val auto = AutoPause()
        auto.onStatus(both, isPlaying = true)
        assertEquals(Action.PAUSE, auto.onStatus(one, isPlaying = true))
        assertEquals(Action.NONE, auto.onStatus(none, isPlaying = false))
        assertEquals(Action.NONE, auto.onStatus(one, isPlaying = false))
        assertEquals(Action.RESUME, auto.onStatus(both, isPlaying = false))
    }

    @Test
    fun `resumes even if music still looks active after our pause`() {
        // Android reports music as active for a few seconds after a pause.
        val auto = AutoPause()
        auto.onStatus(both, isPlaying = true)
        assertEquals(Action.PAUSE, auto.onStatus(one, isPlaying = true))
        assertEquals(Action.RESUME, auto.onStatus(both, isPlaying = true))
        // A later removal pauses again normally.
        assertEquals(Action.PAUSE, auto.onStatus(one, isPlaying = true))
    }

    @Test
    fun `does not resume what it did not pause`() {
        val auto = AutoPause()
        auto.onStatus(one, isPlaying = false)
        assertEquals(Action.NONE, auto.onStatus(both, isPlaying = false))
    }

    @Test
    fun `listening with one bud then removing it pauses`() {
        val auto = AutoPause()
        auto.onStatus(one, isPlaying = true)
        assertEquals(Action.PAUSE, auto.onStatus(none, isPlaying = true))
        assertEquals(Action.RESUME, auto.onStatus(one, isPlaying = false))
    }

    @Test
    fun `ignores single headsets`() {
        val auto = AutoPause()
        val max = PodsStatus.Single(PodsModel.AIRPODS_MAX, PodComponent(5, isCharging = false))
        assertEquals(Action.NONE, auto.onStatus(max, isPlaying = true))
    }
}
