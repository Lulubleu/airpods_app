package com.airpodsapp.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProximityPairingParserTest {

    /** Builds a 27-byte beacon; unspecified bytes are zero. */
    private fun beacon(modelId: Int, status: Int, battery: Int, chargeAndCase: Int): ByteArray =
        ByteArray(27).also {
            it[0] = 0x07
            it[1] = 0x19
            it[2] = 0x01
            it[3] = (modelId shr 8).toByte()
            it[4] = modelId.toByte()
            it[5] = status.toByte()
            it[6] = battery.toByte()
            it[7] = chargeAndCase.toByte()
        }

    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `rejects wrong size or header`() {
        assertNull(ProximityPairingParser.parse(null))
        assertNull(ProximityPairingParser.parse(ByteArray(26)))
        assertNull(ProximityPairingParser.parse(beacon(0x0E20, 0, 0, 0).also { it[0] = 0x10 }))
        assertNull(ProximityPairingParser.parse(beacon(0x0E20, 0, 0, 0).also { it[1] = 0x18 }))
    }

    @Test
    fun `decodes non-flipped stereo beacon`() {
        // status 0x22: orientation bit set (not flipped), low nibble bit 1 -> left in ear
        // battery 0x78: right = 7, left = 8
        // 0x54: charging bits 0b0101 -> left + case charging; case level 4
        val status = assertIs<PodsStatus.Stereo>(ProximityPairingParser.parse(beacon(0x1420, 0x22, 0x78, 0x54)))

        assertEquals(PodsModel.AIRPODS_PRO_2, status.model)
        assertEquals(PodComponent(8, isCharging = true, isInEar = true), status.left)
        assertEquals(PodComponent(7, isCharging = false, isInEar = false), status.right)
        assertEquals(PodComponent(4, isCharging = true), status.case)
        assertEquals(85, status.left.batteryPercent)
        assertEquals(75, status.right.batteryPercent)
        assertEquals(45, status.case.batteryPercent)
        assertFalse(status.isDisconnected)
    }

    @Test
    fun `decodes flipped stereo beacon`() {
        // status 0x02: orientation bit clear (flipped), bit 1 -> right in ear
        // battery 0x78: left = 7, right = 8; charging bit 0 -> right
        val status = assertIs<PodsStatus.Stereo>(ProximityPairingParser.parse(beacon(0x0E20, 0x02, 0x78, 0x1A)))

        assertEquals(PodsModel.AIRPODS_PRO, status.model)
        assertEquals(PodComponent(7, isCharging = false, isInEar = false), status.left)
        assertEquals(PodComponent(8, isCharging = true, isInEar = true), status.right)
        assertEquals(PodComponent(10, isCharging = false), status.case)
        assertEquals(100, status.case.batteryPercent)
    }

    @Test
    fun `absent components`() {
        // Right bud and case absent (0xF), left at 50%.
        val status = assertIs<PodsStatus.Stereo>(ProximityPairingParser.parse(beacon(0x0F20, 0x20, 0xF5, 0x0F)))

        assertTrue(status.left.isConnected)
        assertTrue(status.right.isAbsent)
        assertNull(status.right.batteryPercent)
        assertTrue(status.case.isAbsent)
        assertFalse(status.isDisconnected)

        val gone = assertIs<PodsStatus.Stereo>(ProximityPairingParser.parse(beacon(0x0F20, 0x20, 0xFF, 0x0F)))
        assertTrue(gone.isDisconnected)
    }

    @Test
    fun `decodes single headset`() {
        // AirPods Max (short id 0xA): battery = low nibble of byte 6, charging = bit 0.
        val status = assertIs<PodsStatus.Single>(ProximityPairingParser.parse(beacon(0x0A20, 0x00, 0xF6, 0x10)))

        assertEquals(PodsModel.AIRPODS_MAX, status.model)
        assertEquals(PodComponent(6, isCharging = true), status.battery)
        assertEquals(65, status.battery.batteryPercent)
    }

    @Test
    fun `matches the same characters as OpenPods hex decoding`() {
        // OpenPods reads chars 12/13 (batteries), 14 (charging), 15 (case), 11 (in-ear), 10 (flip).
        val frame = hex("07190127202B98A3".padEnd(54, '0'))
        val status = assertIs<PodsStatus.Stereo>(ProximityPairingParser.parse(frame))

        assertEquals(PodsModel.AIRPODS_PRO_3, status.model) // chars 6..9 = "2720"
        // char 10 = '2' -> not flipped: left = char 13 ('8'), right = char 12 ('9')
        assertEquals(8, status.left.rawLevel)
        assertEquals(9, status.right.rawLevel)
        // char 11 = 'B' = 0b1011 -> bit 1 (left) and bit 3 (right) in ear
        assertTrue(status.left.isInEar)
        assertTrue(status.right.isInEar)
        // char 14 = 'A' = 0b1010 -> right charging only; char 15 = '3' -> case 35%
        assertFalse(status.left.isCharging)
        assertTrue(status.right.isCharging)
        assertFalse(status.case.isCharging)
        assertEquals(35, status.case.batteryPercent)
    }

    @Test
    fun `model identification`() {
        val cases = mapOf(
            0x0220 to PodsModel.AIRPODS_1,
            0x0F20 to PodsModel.AIRPODS_2,
            0x1320 to PodsModel.AIRPODS_3,
            0x0E20 to PodsModel.AIRPODS_PRO,
            0x1420 to PodsModel.AIRPODS_PRO_2,
            0x2420 to PodsModel.AIRPODS_PRO_2,
            0x2720 to PodsModel.AIRPODS_PRO_3,
            0x0A20 to PodsModel.AIRPODS_MAX,
            0x1A20 to PodsModel.AIRPODS_MAX,
            0x0B20 to PodsModel.POWERBEATS_PRO,
            0x0520 to PodsModel.BEATS_X,
            0x1020 to PodsModel.BEATS_FLEX,
            0x0620 to PodsModel.BEATS_SOLO_3,
            0x0920 to PodsModel.BEATS_STUDIO_3,
            0x0320 to PodsModel.POWERBEATS_3,
            0x7720 to PodsModel.UNKNOWN,
        )
        cases.forEach { (id, model) -> assertEquals(model, PodsModel.fromBeacon(id), "id %04X".format(id)) }
    }

    @Test
    fun `scan filter matches header only`() {
        val data = ProximityPairingParser.scanFilterData
        val mask = ProximityPairingParser.scanFilterMask
        assertEquals(27, data.size)
        assertEquals(0x07, data[0].toInt())
        assertEquals(0x19, data[1].toInt())
        assertEquals(listOf(-1, -1), mask.take(2).map { it.toInt() })
        assertTrue(mask.drop(2).all { it.toInt() == 0 })
    }
}
