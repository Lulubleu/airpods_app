package com.airpodsapp.protocol.aap

/**
 * AAP / AACP: Apple's accessory protocol, spoken over a classic Bluetooth L2CAP channel
 * (PSM 0x1001) once the AirPods are connected. Unlike BLE beacons, the AirPods push
 * changes immediately: ear detection, exact battery levels, noise-control mode.
 *
 * Packet layout and values come from LibrePods (GPL-3.0, github.com/kavishdevar/librepods,
 * docs/AAP Definitions.md). Every data packet is `04 00 04 00` + opcode (2 bytes, LE) + payload.
 */
object Aap {
    const val PSM = 0x1001
    const val SERVICE_UUID = "74ec2172-0bad-4d01-8f77-997b2be0722a"

    internal val HEADER = byteArrayOf(0x04, 0x00, 0x04, 0x00)

    internal const val OP_BATTERY = 0x04
    internal const val OP_EAR_DETECTION = 0x06
    internal const val OP_CONTROL = 0x09
    internal const val OP_REQUEST_NOTIFICATIONS = 0x0F
    internal const val OP_FEATURE_FLAGS = 0x4D

    internal const val CONTROL_NOISE_MODE = 0x0D

    /** Must be sent first, otherwise the AirPods ignore every other packet. */
    val handshake: ByteArray = bytes(0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

    /** Enables features Apple gates behind recent OSes (adaptive transparency, conversation awareness). */
    val featureFlags: ByteArray = data(OP_FEATURE_FLAGS, 0xD7, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

    /** Subscribes to all notifications (ear detection, battery, noise control…). */
    val requestNotifications: ByteArray = data(OP_REQUEST_NOTIFICATIONS, 0xFF, 0xFF, 0xFF, 0xFF)

    /** Changes the noise-control mode; the AirPods answer with the same packet. */
    fun setNoiseControl(mode: NoiseControlMode): ByteArray =
        data(OP_CONTROL, CONTROL_NOISE_MODE, mode.value, 0x00, 0x00, 0x00)

    private fun data(opcode: Int, vararg payload: Int): ByteArray =
        HEADER + bytes(opcode, 0x00) + bytes(*payload)

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}

enum class NoiseControlMode(val value: Int) {
    OFF(0x01),
    NOISE_CANCELLATION(0x02),
    TRANSPARENCY(0x03),
    ADAPTIVE(0x04);

    companion object {
        fun from(value: Int) = entries.firstOrNull { it.value == value }
    }
}

/** Ear-detection state of one bud. */
enum class EarState(val value: Int) {
    IN_EAR(0x00),
    OUT_OF_EAR(0x01),
    IN_CASE(0x02);

    companion object {
        fun from(value: Int) = entries.firstOrNull { it.value == value }
    }
}

enum class AapComponent(val value: Int) {
    SINGLE(0x01),
    RIGHT(0x02),
    LEFT(0x04),
    CASE(0x08);

    companion object {
        fun from(value: Int) = entries.firstOrNull { it.value == value }
    }
}

/**
 * Exact battery of one component.
 * @property level 0..100, or null when the AirPods report an invalid value.
 * @property connected false when the component is reported as disconnected (e.g. case closed and away).
 */
data class AapBattery(val level: Int?, val isCharging: Boolean, val connected: Boolean)

/** Messages we understand; everything else is [Unknown]. */
sealed interface AapMessage {
    /** Primary = the bud currently acting as primary (it holds the microphone). */
    data class EarDetection(val primary: EarState?, val secondary: EarState?) : AapMessage

    data class Battery(val components: Map<AapComponent, AapBattery>) : AapMessage

    data class NoiseControl(val mode: NoiseControlMode?) : AapMessage

    data class Unknown(val opcode: Int) : AapMessage
}

object AapParser {
    private const val STATUS_CHARGING = 0x01
    private const val STATUS_DISCONNECTED = 0x04

    /** Decodes one L2CAP packet, or returns null if it is not an AAP data packet. */
    fun parse(packet: ByteArray): AapMessage? {
        if (packet.size < 6 || !packet.startsWith(Aap.HEADER)) return null

        return when (val opcode = packet.u8(4) or (packet.u8(5) shl 8)) {
            Aap.OP_EAR_DETECTION -> {
                if (packet.size < 8) return null
                AapMessage.EarDetection(EarState.from(packet.u8(6)), EarState.from(packet.u8(7)))
            }
            Aap.OP_BATTERY -> parseBattery(packet)
            Aap.OP_CONTROL -> {
                if (packet.size < 8) return null
                if (packet.u8(6) == Aap.CONTROL_NOISE_MODE) {
                    AapMessage.NoiseControl(NoiseControlMode.from(packet.u8(7)))
                } else {
                    AapMessage.Unknown(opcode)
                }
            }
            else -> AapMessage.Unknown(opcode)
        }
    }

    /** `[count]` then `count` × `[component] 01 [level] [status] 01`. */
    private fun parseBattery(packet: ByteArray): AapMessage.Battery? {
        if (packet.size < 7) return null
        val count = packet.u8(6)
        val components = buildMap {
            for (i in 0 until count) {
                val offset = 7 + i * 5
                if (offset + 4 > packet.size) break
                val component = AapComponent.from(packet.u8(offset)) ?: continue
                val level = packet[offset + 2].toInt().takeIf { it in 0..100 }
                val status = packet.u8(offset + 3)
                put(
                    component,
                    AapBattery(
                        level = level,
                        isCharging = status == STATUS_CHARGING,
                        connected = status != STATUS_DISCONNECTED,
                    ),
                )
            }
        }
        return AapMessage.Battery(components)
    }

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.u8(index: Int) = this[index].toInt() and 0xFF
}
