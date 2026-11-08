package com.airpodsapp.protocol

/** A received advertisement, decoupled from Android's `ScanResult`. */
class Beacon(
    /** Random BLE address (rotates over time). */
    val address: String,
    val rssi: Int,
    /** Monotonic receive time, e.g. `ScanResult.timestampNanos`. */
    val timestampNanos: Long,
    val payload: ByteArray,
)

/**
 * Picks which beacon most likely comes from the user's own AirPods.
 *
 * Android hides real BLE addresses and AirPods rotate theirs, so any nearby pair looks the same.
 * Heuristic from OpenPods: among beacons seen in the last [windowNanos], keep the strongest;
 * when it comes from the same address as the new beacon, prefer the new (fresher) one.
 * Results weaker than [minRssi] are dropped.
 *
 * Not thread-safe: call from a single scan callback thread.
 */
class BeaconSelector(
    private val windowNanos: Long = 10_000_000_000L,
    private val minRssi: Int = -60,
) {
    private val recent = ArrayDeque<Beacon>()

    /**
     * Records [beacon] and returns the beacon to decode, or null if none is close enough.
     * [nowNanos] must use the same clock as [Beacon.timestampNanos].
     */
    fun offer(beacon: Beacon, nowNanos: Long = beacon.timestampNanos): Beacon? {
        recent.addLast(beacon)
        recent.removeAll { nowNanos - it.timestampNanos > windowNanos }

        val strongest = recent.maxByOrNull { it.rssi } ?: return null
        val best = if (strongest.address == beacon.address) beacon else strongest
        return best.takeIf { it.rssi >= minRssi }
    }

    fun clear() = recent.clear()
}
