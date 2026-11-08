package com.airpodsapp.protocol.aap

import com.airpodsapp.protocol.PodComponent
import com.airpodsapp.protocol.PodsModel
import com.airpodsapp.protocol.PodsStatus

/** Everything learnt from the AAP connection so far. */
data class AapState(
    val ear: AapMessage.EarDetection? = null,
    val batteries: Map<AapComponent, AapBattery> = emptyMap(),
    /** Null until the AirPods report a mode, i.e. for models without noise control. */
    val noiseMode: NoiseControlMode? = null,
) {
    fun apply(message: AapMessage): AapState = when (message) {
        is AapMessage.EarDetection -> copy(ear = message)
        is AapMessage.Battery -> copy(batteries = batteries + message.components)
        is AapMessage.NoiseControl -> message.mode?.let { copy(noiseMode = it) } ?: this
        is AapMessage.Unknown -> this
    }
}

/**
 * Combines the BLE beacon (model, orientation, 10% battery) with the AAP state
 * (instant ear detection, exact battery). AAP wins wherever it has data.
 * Works with either source missing; returns null if both are.
 */
fun mergeStatus(ble: PodsStatus?, aap: AapState?): PodsStatus? {
    if (aap == null || (aap.ear == null && aap.batteries.isEmpty())) return ble

    return when (ble) {
        is PodsStatus.Single -> ble.copy(battery = ble.battery.withAap(aap.batteries[AapComponent.SINGLE]))
        is PodsStatus.Stereo -> mergeStereo(ble, aap)
        null -> {
            // No beacon yet: build the status from AAP alone.
            aap.batteries[AapComponent.SINGLE]?.let { single ->
                return PodsStatus.Single(PodsModel.UNKNOWN, PodComponent(PodComponent.ABSENT, false).withAap(single))
            }
            val absent = PodComponent(PodComponent.ABSENT, isCharging = false)
            mergeStereo(PodsStatus.Stereo(PodsModel.UNKNOWN, absent, absent, absent), aap)
        }
    }
}

private fun mergeStereo(ble: PodsStatus.Stereo, aap: AapState): PodsStatus.Stereo {
    val ear = aap.ear
    // AAP reports primary/secondary; the beacon tells which side is primary.
    val leftEar = if (ble.primaryIsLeft) ear?.primary else ear?.secondary
    val rightEar = if (ble.primaryIsLeft) ear?.secondary else ear?.primary

    return ble.copy(
        left = ble.left.withAap(aap.batteries[AapComponent.LEFT], leftEar),
        right = ble.right.withAap(aap.batteries[AapComponent.RIGHT], rightEar),
        case = ble.case.withAap(aap.batteries[AapComponent.CASE]),
    )
}

private fun PodComponent.withAap(battery: AapBattery?, ear: EarState? = null): PodComponent {
    var result = this
    if (battery != null) {
        result = if (battery.connected && battery.level != null) {
            result.copy(exactPercent = battery.level, isCharging = battery.isCharging)
        } else {
            result.copy(rawLevel = PodComponent.ABSENT, exactPercent = null, isCharging = false)
        }
    }
    if (ear != null) result = result.copy(isInEar = ear == EarState.IN_EAR)
    return result
}
