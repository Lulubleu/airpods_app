package com.airpodsapp.bluetooth

import com.airpodsapp.protocol.PodsStatus
import com.airpodsapp.protocol.aap.AapMessage
import com.airpodsapp.protocol.aap.AapState
import com.airpodsapp.protocol.aap.NoiseControlMode
import com.airpodsapp.protocol.aap.mergeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the app knows about the user's AirPods. */
data class PodsState(
    /** Name of the connected Apple device, or null when none is connected. */
    val deviceName: String? = null,
    /** Last BLE beacon merged with AAP data; null until either arrives. */
    val status: PodsStatus? = null,
    /** `System.currentTimeMillis()` of the last update, 0 if none. */
    val lastSeenMillis: Long = 0,
    /** Raw BLE beacon, kept to re-merge when AAP data changes. */
    val bleStatus: PodsStatus? = null,
    /** AAP link data; null while the L2CAP link is down. */
    val aap: AapState? = null,
) {
    val isConnected: Boolean get() = deviceName != null

    /** Current noise-control mode, or null if the AirPods have none (or the link is down). */
    val noiseMode: NoiseControlMode? get() = aap?.noiseMode
}

/**
 * Single source of truth shared by the service (writer) and the UI and notification (readers).
 * Process-wide: the service and the activity run in the same process.
 */
object PodsRepository {
    private val _state = MutableStateFlow(PodsState())
    val state: StateFlow<PodsState> = _state.asStateFlow()

    /** Set by the service while the AAP link is up; used by the UI to change the noise mode. */
    @Volatile var noiseControlSender: ((NoiseControlMode) -> Unit)? = null

    fun onConnected(deviceName: String?) = _state.update {
        it.copy(deviceName = deviceName ?: it.deviceName ?: "AirPods")
    }

    fun onStatus(status: PodsStatus, nowMillis: Long = System.currentTimeMillis()) = _state.update {
        it.copy(bleStatus = status, status = mergeStatus(status, it.aap), lastSeenMillis = nowMillis)
    }

    fun onAapLinked() = _state.update { it.copy(aap = AapState()) }

    fun onAapMessage(message: AapMessage, nowMillis: Long = System.currentTimeMillis()) = _state.update {
        val aap = (it.aap ?: AapState()).apply(message)
        val merged = mergeStatus(it.bleStatus, aap)
        it.copy(aap = aap, status = merged, lastSeenMillis = if (merged != it.status) nowMillis else it.lastSeenMillis)
    }

    fun onAapClosed() = _state.update { it.copy(aap = null, status = it.bleStatus) }

    fun setNoiseControl(mode: NoiseControlMode) {
        noiseControlSender?.invoke(mode)
    }

    fun onDisconnected() {
        noiseControlSender = null
        _state.value = PodsState()
    }
}
