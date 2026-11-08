package com.airpodsapp.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.ParcelUuid
import android.util.Log
import com.airpodsapp.protocol.aap.Aap
import com.airpodsapp.protocol.aap.AapMessage
import com.airpodsapp.protocol.aap.AapParser
import com.airpodsapp.protocol.aap.NoiseControlMode
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.IOException
import kotlin.concurrent.thread

/**
 * AAP link to connected AirPods over classic Bluetooth L2CAP (PSM 0x1001).
 *
 * Runs a reader thread that decodes each packet and forwards it to [onMessage].
 * [onOpened] is called once the link is up; [onClosed] once when it ends (AirPods
 * disconnected, connection refused or [close]), with whether it had been opened.
 * Callbacks run on the reader thread. Requires BLUETOOTH_CONNECT.
 */
class AapConnection(
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val onOpened: () -> Unit,
    private val onMessage: (AapMessage) -> Unit,
    private val onClosed: (wasOpen: Boolean) -> Unit,
) {
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var closed = false

    fun open() {
        thread(name = "aap-reader", isDaemon = true) { run() }
    }

    fun setNoiseControl(mode: NoiseControlMode) = send(Aap.setNoiseControl(mode))

    fun close() {
        closed = true
        try {
            socket?.close()
        } catch (_: IOException) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun run() {
        var opened = false
        try {
            val s = createSocket()
            socket = s
            s.connect()
            Log.i(TAG, "AAP connected")
            // Same opening sequence as LibrePods: handshake, feature flags, notifications.
            send(Aap.handshake)
            send(Aap.featureFlags)
            send(Aap.requestNotifications)
            opened = true
            onOpened()

            val buffer = ByteArray(1024)
            while (!closed) {
                val read = s.inputStream.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                val packet = buffer.copyOf(read)
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "rx " + packet.joinToString(" ") { "%02X".format(it) })
                }
                AapParser.parse(packet)?.let(onMessage)
            }
        } catch (e: Exception) {
            if (!closed) Log.w(TAG, "AAP link failed: ${e.message}")
        } finally {
            val requested = closed
            close()
            if (!requested) onClosed(opened)
        }
    }

    private fun send(packet: ByteArray): Boolean = try {
        socket?.outputStream?.apply { write(packet); flush() } != null
    } catch (e: IOException) {
        Log.w(TAG, "AAP send failed: ${e.message}")
        false
    }

    /**
     * Android has no public API for a classic (BR/EDR) L2CAP socket on a fixed PSM:
     * `createUsingSocketSettings` (API 36) rejects TYPE_L2CAP ("invalid socketType - 3",
     * tested on Android 17) and only takes LE PSMs ≤ 255. So we use the hidden constructor,
     * like LibrePods. The AirPods only accept the channel on recent stacks (Android 17+).
     */
    private fun createSocket(): BluetoothSocket {
        // Hidden constructors are filtered out for apps; exempt only BluetoothSocket.
        HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/BluetoothSocket;")
        val uuid = ParcelUuid.fromString(Aap.SERVICE_UUID)
        val type = BluetoothSocket.TYPE_L2CAP
        // Constructor signatures seen across Android versions (from LibrePods).
        val candidates = listOf(
            arrayOf<Any>(adapter, device, type, true, true, Aap.PSM, uuid),
            arrayOf<Any>(device, type, true, true, Aap.PSM, uuid),
            arrayOf<Any>(device, type, 1, true, true, Aap.PSM, uuid),
        )
        var last: Exception? = null
        for (args in candidates) {
            try {
                val types = args.map { it::class.javaPrimitiveType ?: it::class.java }.toTypedArray()
                val constructor = BluetoothSocket::class.java.getDeclaredConstructor(*types)
                constructor.isAccessible = true
                return (constructor.newInstance(*args) as BluetoothSocket).also { Log.i(TAG, "Socket via reflection") }
            } catch (e: Exception) {
                last = e
            }
        }
        val available = BluetoothSocket::class.java.declaredConstructors.joinToString("; ") { c ->
            c.parameterTypes.joinToString(", ", "(", ")") { it.simpleName }
        }
        Log.w(TAG, "Hidden constructors available: $available", last)
        throw IOException("No usable L2CAP socket constructor", last)
    }

    private companion object {
        const val TAG = "AapConnection"
    }
}
