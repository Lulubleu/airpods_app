package com.airpodsapp.protocol

/**
 * One battery-powered component (left bud, right bud, case, or a single headset).
 *
 * @property rawLevel battery nibble from the beacon: 0..10 = level, 15 = absent.
 */
data class PodComponent(
    val rawLevel: Int,
    val isCharging: Boolean,
    val isInEar: Boolean = false,
    /** Exact level from the AAP connection, when available (overrides [rawLevel]). */
    val exactPercent: Int? = null,
) {
    /** The component reported a battery level (it is in range / in the case). */
    val isConnected: Boolean get() = exactPercent != null || rawLevel <= MAX_LEVEL

    /** Explicitly reported as absent. Values 11..14 are neither connected nor absent. */
    val isAbsent: Boolean get() = exactPercent == null && rawLevel == ABSENT

    /**
     * Battery in percent, or null when unknown. BLE beacons only have 10% steps,
     * so levels 0..9 are reported as the middle of their range (5%, 15%… 95%).
     */
    val batteryPercent: Int?
        get() = when {
            exactPercent != null -> exactPercent
            rawLevel == MAX_LEVEL -> 100
            rawLevel < MAX_LEVEL -> rawLevel * 10 + 5
            else -> null
        }

    val isLowBattery: Boolean
        get() = exactPercent?.let { it <= LOW_PERCENT } ?: (rawLevel <= LOW_LEVEL)

    companion object {
        const val MAX_LEVEL = 10
        const val LOW_LEVEL = 1
        const val LOW_PERCENT = 15
        const val ABSENT = 15
    }
}

/** Decoded content of one Proximity Pairing beacon. */
sealed interface PodsStatus {
    val model: PodsModel

    /** True when no component reports a battery level. */
    val isDisconnected: Boolean

    /** Earbuds with a charging case (AirPods, AirPods Pro, Powerbeats Pro…). */
    data class Stereo(
        override val model: PodsModel,
        val left: PodComponent,
        val right: PodComponent,
        val case: PodComponent,
        /** Which bud is primary (sends the beacon, holds the mic); maps AAP primary/secondary to sides. */
        val primaryIsLeft: Boolean = true,
    ) : PodsStatus {
        override val isDisconnected: Boolean
            get() = left.isAbsent && right.isAbsent && case.isAbsent
    }

    /** Single-battery headsets (AirPods Max, most Beats). */
    data class Single(
        override val model: PodsModel,
        val battery: PodComponent,
    ) : PodsStatus {
        override val isDisconnected: Boolean get() = battery.isAbsent
    }
}
