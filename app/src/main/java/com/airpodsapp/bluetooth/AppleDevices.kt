package com.airpodsapp.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Recognises Apple/Beats audio devices among classic Bluetooth connections. */
object AppleDevices {
    /** Service UUIDs announced by AirPods and Beats (from OpenPods). */
    private val APPLE_AUDIO_UUIDS = setOf(
        ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a"),
        ParcelUuid.fromString("2a72e02b-7b99-778f-014d-ad0b7221ec74"),
    )

    /** Requires BLUETOOTH_CONNECT (checked by the callers). */
    @SuppressLint("MissingPermission")
    fun isAppleAudio(device: BluetoothDevice): Boolean =
        device.uuids?.any { it in APPLE_AUDIO_UUIDS } == true

    @SuppressLint("MissingPermission")
    fun displayName(device: BluetoothDevice): String? = device.alias ?: device.name

    /**
     * Returns the Apple audio device already connected through the headset profile, if any.
     * Used at app start, since the ACL_CONNECTED broadcast may have happened before install.
     */
    @SuppressLint("MissingPermission")
    suspend fun findConnected(context: Context): BluetoothDevice? {
        if (!PodsPermissions.hasConnect(context)) return null
        val adapter: BluetoothAdapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return null
        if (!adapter.isEnabled) return null

        return suspendCancellableCoroutine { cont ->
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    val device = proxy.connectedDevices.firstOrNull(::isAppleAudio)
                    adapter.closeProfileProxy(profile, proxy)
                    if (cont.isActive) cont.resume(device)
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            }
            if (!adapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET)) {
                cont.resume(null)
            }
        }
    }
}
