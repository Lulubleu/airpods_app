package com.airpodsapp.protocol

/**
 * Decodes Apple "Proximity Pairing" BLE advertisements (manufacturer data, company id 0x004C).
 *
 * Algorithm ported from OpenPods (GPL-3.0, github.com/adolfintel/OpenPods), working on bytes
 * instead of a hex string. Byte layout (Android strips the 2-byte company id):
 *
 * ```
 * [0]    0x07  message type
 * [1]    0x19  remaining length (25)
 * [3..4]       model id
 * [5]          high nibble bit 0x2: orientation; low nibble: in-ear bits
 * [6]          battery of both buds (one nibble each)
 * [7]          high nibble: charging flags; low nibble: case battery
 * ```
 *
 * See docs/ANALYSE_BLE.md for the full write-up.
 */
object ProximityPairingParser {
    const val APPLE_COMPANY_ID = 0x004C
    const val MESSAGE_TYPE = 0x07
    const val MESSAGE_LENGTH = 0x19
    const val PAYLOAD_SIZE = 27

    /** Data/mask pair for an Android `ScanFilter` on [APPLE_COMPANY_ID]: matches bytes 0 and 1. */
    val scanFilterData: ByteArray
        get() = ByteArray(PAYLOAD_SIZE).also {
            it[0] = MESSAGE_TYPE.toByte()
            it[1] = MESSAGE_LENGTH.toByte()
        }

    val scanFilterMask: ByteArray
        get() = ByteArray(PAYLOAD_SIZE).also {
            it[0] = 0xFF.toByte()
            it[1] = 0xFF.toByte()
        }

    /** True if [data] has the size and header of a Proximity Pairing message. */
    fun isProximityPairing(data: ByteArray?): Boolean =
        data != null &&
            data.size == PAYLOAD_SIZE &&
            data.u8(0) == MESSAGE_TYPE &&
            data.u8(1) == MESSAGE_LENGTH

    /** Decodes [data], or returns null if it is not a Proximity Pairing message. */
    fun parse(data: ByteArray?): PodsStatus? {
        if (data == null || !isProximityPairing(data)) return null

        val model = PodsModel.fromBeacon((data.u8(3) shl 8) or data.u8(4))

        val status = data.u8(5)
        val inEarBits = status and 0x0F
        // The beacon is sent by one bud and reports "this bud / the other bud";
        // this bit tells which side is which.
        val flipped = status and 0x20 == 0

        val battery = data.u8(6)
        val batteryHigh = battery shr 4
        val batteryLow = battery and 0x0F

        val chargeAndCase = data.u8(7)
        val chargingBits = chargeAndCase shr 4
        val caseLevel = chargeAndCase and 0x0F

        if (model.isSingle) {
            return PodsStatus.Single(
                model = model,
                battery = PodComponent(batteryLow, isCharging = chargingBits.bit(0)),
            )
        }

        val left = PodComponent(
            rawLevel = if (flipped) batteryHigh else batteryLow,
            isCharging = chargingBits.bit(if (flipped) 1 else 0),
            isInEar = inEarBits.bit(if (flipped) 3 else 1),
        )
        val right = PodComponent(
            rawLevel = if (flipped) batteryLow else batteryHigh,
            isCharging = chargingBits.bit(if (flipped) 0 else 1),
            isInEar = inEarBits.bit(if (flipped) 1 else 3),
        )
        val case = PodComponent(caseLevel, isCharging = chargingBits.bit(2))

        return PodsStatus.Stereo(model, left, right, case, primaryIsLeft = !flipped)
    }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

    private fun Int.bit(index: Int): Boolean = (this shr index) and 1 == 1
}
