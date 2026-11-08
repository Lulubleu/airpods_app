package com.airpodsapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.airpodsapp.R
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the launcher icon (round, squircle, themed) and the status-bar icon for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1000dp-h300dp")
class IconScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun launcherIcon() {
        compose.setContent {
            Row(
                Modifier.background(Color(0xFF202124)).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Round mask (Pixel launcher).
                Box(Modifier.size(216.dp).clip(CircleShape).background(colorResource(android.R.color.system_accent1_100))) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(216.dp))
                }
                // Squircle mask.
                Box(
                    Modifier.size(216.dp).clip(RoundedCornerShape(64.dp))
                        .background(colorResource(android.R.color.system_accent1_100)),
                ) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(216.dp))
                }
                // Themed (monochrome) icon.
                Box(Modifier.size(216.dp).clip(CircleShape).background(Color(0xFF3B4A3F))) {
                    Image(
                        painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(216.dp),
                        colorFilter = ColorFilter.tint(Color(0xFFCDE8D3)),
                    )
                }
                // Status-bar icon, enlarged.
                Box(Modifier.size(216.dp).background(Color.Black).padding(48.dp)) {
                    Image(painterResource(R.drawable.ic_stat_pods), null, Modifier.size(120.dp))
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/launcher_icon.png")
    }
}
