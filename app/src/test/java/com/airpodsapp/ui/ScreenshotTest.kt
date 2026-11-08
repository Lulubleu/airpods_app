package com.airpodsapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.airpodsapp.bluetooth.PodsState
import com.airpodsapp.protocol.PodComponent
import com.airpodsapp.protocol.PodsModel
import com.airpodsapp.protocol.PodsStatus
import com.airpodsapp.protocol.aap.AapMessage
import com.airpodsapp.protocol.aap.AapState
import com.airpodsapp.protocol.aap.EarState
import com.airpodsapp.protocol.aap.NoiseControlMode
import com.airpodsapp.ui.home.HomeScreen
import com.airpodsapp.ui.theme.PodsTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens to PNG (app/build/outputs/roborazzi) without a device.
 * Run: ./gradlew :app:recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val connected = PodsState(
        deviceName = "Mes AirPods Pro",
        status = PodsStatus.Stereo(
            model = PodsModel.AIRPODS_PRO_2,
            left = PodComponent(8, isCharging = false, isInEar = true),
            right = PodComponent(1, isCharging = false, isInEar = true),
            case = PodComponent(4, isCharging = true),
        ),
        lastSeenMillis = System.currentTimeMillis(),
    )

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            PodsTheme {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun status() = capture("status_light") { HomeScreen(connected, hasPermission = true, onRequestPermission = {}) }

    @Test
    @Config(qualifiers = "+night")
    fun statusDark() = capture("status_dark") { HomeScreen(connected, hasPermission = true, onRequestPermission = {}) }

    @Test
    @Config(qualifiers = "+night")
    fun liveWithNoiseControl() = capture("live_anc_dark") {
        val aap = AapState(noiseMode = NoiseControlMode.NOISE_CANCELLATION)
            .apply(AapMessage.EarDetection(EarState.IN_EAR, EarState.IN_EAR))
        HomeScreen(connected.copy(aap = aap), hasPermission = true, onRequestPermission = {})
    }

    @Test
    fun searching() = capture("searching") { HomeScreen(PodsState(), hasPermission = true, onRequestPermission = {}) }

    @Test
    fun permission() = capture("permission") { HomeScreen(PodsState(), hasPermission = false, onRequestPermission = {}) }

    @Test
    @Config(qualifiers = "+night")
    fun single() = capture("single_dark") {
        HomeScreen(
            PodsState(
                deviceName = "AirPods Max",
                status = PodsStatus.Single(PodsModel.AIRPODS_MAX, PodComponent(6, isCharging = true)),
                lastSeenMillis = System.currentTimeMillis() - 42_000,
            ),
            hasPermission = true,
            onRequestPermission = {},
        )
    }
}
