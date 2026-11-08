package com.airpodsapp.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.airpodsapp.protocol.Beacon
import com.airpodsapp.protocol.BeaconSelector
import com.airpodsapp.protocol.PodsStatus
import com.airpodsapp.protocol.ProximityPairingParser

/**
 * BLE scanner for AirPods "Proximity Pairing" beacons.
 *
 * Filters on Apple manufacturer data in hardware, picks the most likely beacon with
 * [BeaconSelector], decodes it and forwards the result to [onStatus].
 */
class PodsScanner(
    context: Context,
    private val onStatus: (PodsStatus) -> Unit,
) {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val selector = BeaconSelector()
    private var scanner: BluetoothLeScanner? = null

    val isRunning: Boolean get() = scanner != null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)

        override fun onBatchScanResults(results: List<ScanResult>) = results.forEach(::handle)

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed: $errorCode")
            scanner = null
        }
    }

    /** Requires BLUETOOTH_SCAN. Returns false if Bluetooth is off or unavailable. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRunning) return true
        val leScanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return false

        val filter = ScanFilter.Builder()
            .setManufacturerData(
                ProximityPairingParser.APPLE_COMPANY_ID,
                ProximityPairingParser.scanFilterData,
                ProximityPairingParser.scanFilterMask,
            )
            .build()
        val settings = ScanSettings.Builder()
            // We only scan while AirPods are connected, so low latency is affordable.
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            // Deliver each beacon immediately. OpenPods used 1 ms, but any non-zero delay enables
            // hardware batching: on a Pixel 10a results then arrived in bursts every ~5 s,
            // which delayed ear detection.
            .setReportDelay(0)
            .build()

        return try {
            leScanner.startScan(listOf(filter), settings, callback)
            scanner = leScanner
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing Bluetooth permission", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            scanner?.stopScan(callback)
        } catch (e: Exception) {
            // Adapter already off or permission revoked: nothing left to stop.
            Log.w(TAG, "stopScan failed", e)
        }
        scanner = null
        selector.clear()
    }

    private fun handle(result: ScanResult) {
        val data = result.scanRecord?.getManufacturerSpecificData(ProximityPairingParser.APPLE_COMPANY_ID)
        if (!ProximityPairingParser.isProximityPairing(data)) return

        val beacon = Beacon(result.device.address, result.rssi, result.timestampNanos, data!!)
        val best = selector.offer(beacon, SystemClock.elapsedRealtimeNanos())
        if (Log.isLoggable(TAG, Log.DEBUG)) logBeacon(beacon, best)
        best ?: return
        ProximityPairingParser.parse(best.payload)?.let(onStatus)
    }

    /** Enable with `adb shell setprop log.tag.PodsScanner DEBUG`. */
    private fun logBeacon(beacon: Beacon, chosen: Beacon?) {
        val decision = when {
            chosen == null -> "dropped"
            chosen === beacon -> "used"
            else -> "replaced by ${chosen.address.takeLast(5)}@${chosen.rssi}"
        }
        val head = beacon.payload.take(8).joinToString("") { "%02X".format(it) }
        Log.d(TAG, "beacon ${beacon.address.takeLast(5)} rssi=${beacon.rssi} data=$head -> $decision")
    }

    private companion object {
        const val TAG = "PodsScanner"
    }
}
