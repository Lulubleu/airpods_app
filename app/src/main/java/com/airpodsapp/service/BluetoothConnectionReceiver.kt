package com.airpodsapp.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.airpodsapp.bluetooth.AppleDevices
import com.airpodsapp.bluetooth.PodsPermissions

/**
 * Starts [PodsService] when Apple headphones connect and stops it when they disconnect.
 *
 * Android lets an app start a foreground service from the background when it receives a
 * Bluetooth broadcast that requires BLUETOOTH_CONNECT, which is the case for ACL events.
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED && action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        // Reading the device's UUIDs and name needs BLUETOOTH_CONNECT.
        if (!PodsPermissions.hasConnect(context)) return

        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?: return
        if (!AppleDevices.isAppleAudio(device)) return

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> PodsService.start(context, device)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> PodsService.stop(context)
        }
    }
}
