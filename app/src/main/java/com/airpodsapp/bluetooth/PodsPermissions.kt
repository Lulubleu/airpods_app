package com.airpodsapp.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Runtime permissions the app needs. */
object PodsPermissions {
    /** Needed to scan beacons and identify connected AirPods. */
    val bluetooth = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )

    /** Everything to request from the UI (notifications are optional but asked together). */
    val all: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetooth + Manifest.permission.POST_NOTIFICATIONS
        } else {
            bluetooth
        }

    fun hasBluetooth(context: Context): Boolean = bluetooth.all { context.isGranted(it) }

    fun hasConnect(context: Context): Boolean = context.isGranted(Manifest.permission.BLUETOOTH_CONNECT)

    private fun Context.isGranted(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
