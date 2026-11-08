package com.airpodsapp.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.airpodsapp.bluetooth.AapConnection
import com.airpodsapp.bluetooth.AppleDevices
import com.airpodsapp.bluetooth.PodsPermissions
import com.airpodsapp.bluetooth.PodsRepository
import com.airpodsapp.bluetooth.PodsScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.airpodsapp.protocol.AutoPause
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Foreground service that runs only while Apple headphones are connected:
 * scans their BLE beacons, publishes the decoded status to [PodsRepository]
 * and keeps the battery notification up to date.
 *
 * Started by [BluetoothConnectionReceiver] on ACL_CONNECTED (or by the UI when the
 * AirPods were already connected) and stopped on disconnect or when Bluetooth turns off.
 */
class PodsService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var scanner: PodsScanner

    /** Whether the connection heads-up was already shown for this connection. */
    private var announced = false

    private val autoPause = AutoPause()
    private lateinit var media: MediaControl

    private var deviceAddress: String? = null
    @Volatile private var aap: AapConnection? = null
    private var aapAttempts = 0

    /** Stops everything when Bluetooth is switched off. */
    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PodsNotifications.createChannels(this)
        scanner = PodsScanner(this, PodsRepository::onStatus)
        media = MediaControl(this)
        // Sent by the Bluetooth process, hence exported.
        ContextCompat.registerReceiver(
            this,
            adapterReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )

        // Refresh the notification only when the decoded status (not just the timestamp) changes.
        PodsRepository.state
            .map { it.deviceName to it.status }
            .distinctUntilChanged()
            .onEach { (_, status) ->
                val state = PodsRepository.state.value
                PodsNotifications.update(this, state)
                // First battery reading of this connection: show the connection pop-up.
                if (status != null && !announced) {
                    announced = true
                    PodsNotifications.showConnected(this, state)
                }
            }
            .launchIn(scope)

        // Ear detection: pause when a bud leaves the ear, resume when it is back.
        PodsRepository.state
            .map { it.status }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { status ->
                val playing = media.isPlaying
                val action = autoPause.onStatus(status, playing)
                if (action != AutoPause.Action.NONE) Log.i(TAG, "Ear detection: $action (musicActive=$playing)")
                when (action) {
                    AutoPause.Action.PAUSE -> media.pause()
                    AutoPause.Action.RESUME -> media.play()
                    AutoPause.Action.NONE -> Unit
                }
            }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_DEVICE_NAME)?.let(PodsRepository::onConnected)
        intent?.getStringExtra(EXTRA_DEVICE_ADDRESS)?.let { deviceAddress = it }

        // startForeground must be called quickly after startForegroundService, even if we stop right after.
        try {
            startForeground(
                PodsNotifications.NOTIFICATION_ID,
                PodsNotifications.build(this, PodsRepository.state.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Not allowed to start in foreground", e)
            stopSelf()
            return START_NOT_STICKY
        } catch (e: SecurityException) {
            // connectedDevice type requires the Bluetooth permissions.
            Log.w(TAG, "Missing permission for connectedDevice service", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!PodsPermissions.hasBluetooth(this) || !scanner.start()) {
            stopSelf()
            return START_NOT_STICKY
        }
        connectAap()
        // Not sticky: the next ACL_CONNECTED broadcast restarts us if needed.
        return START_NOT_STICKY
    }

    /**
     * Opens the AAP link (instant ear detection, exact battery, noise control).
     * BLE beacons keep working if it fails, e.g. on Android versions whose
     * Bluetooth stack rejects Apple's L2CAP channel.
     */
    private fun connectAap() {
        if (aap != null) return
        val address = deviceAddress ?: return
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val device = adapter.getRemoteDevice(address)

        aap = AapConnection(
            adapter = adapter,
            device = device,
            onOpened = {
                aapAttempts = 0
                PodsRepository.onAapLinked()
                PodsRepository.noiseControlSender = { mode -> aap?.setNoiseControl(mode) }
            },
            onMessage = PodsRepository::onAapMessage,
            onClosed = { wasOpen ->
                scope.launch {
                    aap = null
                    PodsRepository.noiseControlSender = null
                    PodsRepository.onAapClosed()
                    // The AirPods may not accept the channel right after connecting: retry a few times.
                    if (aapAttempts < MAX_AAP_ATTEMPTS) {
                        aapAttempts++
                        delay(if (wasOpen) 1_000L else aapAttempts * 3_000L)
                        connectAap()
                    }
                }
            },
        ).also { it.open() }
    }

    override fun onDestroy() {
        aap?.close()
        aap = null
        scanner.stop()
        unregisterReceiver(adapterReceiver)
        scope.cancel()
        PodsRepository.onDisconnected()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PodsService"
        private const val EXTRA_DEVICE_NAME = "device_name"
        private const val EXTRA_DEVICE_ADDRESS = "device_address"
        private const val MAX_AAP_ATTEMPTS = 3

        /** Requires BLUETOOTH_CONNECT (to read the device name). */
        fun start(context: Context, device: BluetoothDevice) {
            val intent = Intent(context, PodsService::class.java)
                .putExtra(EXTRA_DEVICE_NAME, AppleDevices.displayName(device))
                .putExtra(EXTRA_DEVICE_ADDRESS, device.address)
            try {
                context.startForegroundService(intent)
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Background start refused", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PodsService::class.java))
        }
    }
}
