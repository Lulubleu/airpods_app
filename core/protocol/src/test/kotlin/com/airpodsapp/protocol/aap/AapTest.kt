package com.airpodsapp.protocol.aap

import com.airpodsapp.protocol.PodComponent
import com.airpodsapp.protocol.PodsModel
import com.airpodsapp.protocol.PodsStatus
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AapTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `outgoing packets match LibrePods`() {
        assertContentEquals(hex("00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00"), Aap.handshake)
        assertContentEquals(hex("04 00 04 00 0F 00 FF FF FF FF"), Aap.requestNotifications)
        assertContentEquals(hex("04 00 04 00 4D 00 D7 00 00 00 00 00 00 00"), Aap.featureFlags)
        assertContentEquals(hex("04 00 04 00 09 00 0D 03 00 00 00"), Aap.setNoiseControl(NoiseControlMode.TRANSPARENCY))
    }

    @Test
    fun `parses battery packet from the docs`() {
        // Left 100% discharging, right 99% charging, case 17% discharging.
        val message = assertIs<AapMessage.Battery>(
            AapParser.parse(hex("04 00 04 00 04 00 03 02 01 64 02 01 04 01 63 01 01 08 01 11 02 01")),
        )
        assertEquals(AapBattery(100, isCharging = false, connected = true), message.components[AapComponent.RIGHT])
        assertEquals(AapBattery(99, isCharging = true, connected = true), message.components[AapComponent.LEFT])
        assertEquals(AapBattery(17, isCharging = false, connected = true), message.components[AapComponent.CASE])
    }

    @Test
    fun `parses disconnected and invalid battery levels`() {
        val message = assertIs<AapMessage.Battery>(AapParser.parse(hex("04 00 04 00 04 00 01 08 01 FF 04 01")))
        assertEquals(AapBattery(null, isCharging = false, connected = false), message.components[AapComponent.CASE])
    }

    @Test
    fun `parses ear detection and noise control`() {
        assertEquals(
            AapMessage.EarDetection(EarState.IN_EAR, EarState.OUT_OF_EAR),
            AapParser.parse(hex("04 00 04 00 06 00 00 01")),
        )
        assertEquals(
            AapMessage.NoiseControl(NoiseControlMode.NOISE_CANCELLATION),
            AapParser.parse(hex("04 00 04 00 09 00 0D 02 00 00 00")),
        )
        assertEquals(AapMessage.Unknown(0x09), AapParser.parse(hex("04 00 04 00 09 00 28 01 00 00 00")))
        assertNull(AapParser.parse(hex("01 02 03 04 05 06")))
    }

    @Test
    fun `merge prefers AAP data and maps primary to the right side`() {
        val absentCase = PodComponent(15, isCharging = false)
        val ble = PodsStatus.Stereo(
            PodsModel.AIRPODS_2,
            left = PodComponent(9, isCharging = false, isInEar = true),
            right = PodComponent(9, isCharging = false, isInEar = true),
            case = absentCase,
            primaryIsLeft = false,
        )
        val aap = AapState()
            .apply(AapMessage.EarDetection(primary = EarState.IN_EAR, secondary = EarState.OUT_OF_EAR))
            .apply(
                AapMessage.Battery(
                    mapOf(
                        AapComponent.LEFT to AapBattery(87, isCharging = false, connected = true),
                        AapComponent.CASE to AapBattery(42, isCharging = true, connected = true),
                    ),
                ),
            )

        val merged = assertIs<PodsStatus.Stereo>(mergeStatus(ble, aap))
        // Primary is the right bud: right in ear, left out.
        assertTrue(merged.right.isInEar)
        assertFalse(merged.left.isInEar)
        assertEquals(87, merged.left.batteryPercent)
        assertEquals(95, merged.right.batteryPercent) // still from BLE
        assertEquals(42, merged.case.batteryPercent)
        assertTrue(merged.case.isCharging)
    }

    @Test
    fun `merge works before the first beacon`() {
        val aap = AapState().apply(
            AapMessage.Battery(mapOf(AapComponent.LEFT to AapBattery(50, isCharging = false, connected = true))),
        )
        val merged = assertIs<PodsStatus.Stereo>(mergeStatus(null, aap))
        assertEquals(50, merged.left.batteryPercent)
        assertTrue(merged.right.isAbsent)
        assertNull(mergeStatus(null, AapState()))
    }
}
