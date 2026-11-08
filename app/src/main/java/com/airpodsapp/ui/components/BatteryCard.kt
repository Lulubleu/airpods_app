package com.airpodsapp.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airpodsapp.R
import com.airpodsapp.protocol.PodComponent

private val PodComponent.fraction: Float? get() = batteryPercent?.div(100f)

/** Battery card of one component: ring + illustration, animated percentage, charging and in-ear badges. */
@Composable
fun BatteryCard(
    label: String,
    pod: PodComponent,
    part: PodPart,
    modifier: Modifier = Modifier,
    shortStem: Boolean = false,
    horizontal: Boolean = false,
) {
    val contentAlpha by animateFloatAsState(if (pod.isConnected) 1f else 0.45f, label = "absent-alpha")

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        val ring: @Composable () -> Unit = {
            BatteryRing(
                level = pod.fraction,
                charging = pod.isCharging,
                low = pod.isLowBattery,
                modifier = Modifier.size(if (horizontal) 88.dp else 112.dp),
            ) {
                PodIllustration(
                    part = part,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize().padding(22.dp),
                    shortStem = shortStem,
                )
            }
        }
        val texts: @Composable () -> Unit = {
            Column(horizontalAlignment = if (horizontal) Alignment.Start else Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Percent(pod.batteryPercent, MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(4.dp))
                Badges(pod)
            }
        }

        if (horizontal) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp).alpha(contentAlpha),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ring()
                texts()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp).alpha(contentAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ring()
                texts()
            }
        }
    }
}

/** Compact gauge for the connection pop-up. */
@Composable
fun MiniBattery(label: String, pod: PodComponent, part: PodPart, shortStem: Boolean = false, ringSize: Dp = 72.dp) {
    Column(
        modifier = Modifier.alpha(if (pod.isConnected) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BatteryRing(pod.fraction, pod.isCharging, pod.isLowBattery, Modifier.size(ringSize), strokeWidth = 6.dp) {
            PodIllustration(part, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxSize().padding(16.dp), shortStem)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Percent(pod.batteryPercent, MaterialTheme.typography.titleLarge)
            if (pod.isCharging) ChargingIcon(Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Percentage that rolls vertically when it changes ("—" when unknown). */
@Composable
private fun Percent(percent: Int?, style: TextStyle) {
    AnimatedContent(
        targetState = percent,
        transitionSpec = {
            val up = (targetState ?: 0) > (initialState ?: 0)
            (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                (slideOutVertically { if (up) -it else it } + fadeOut())
        },
        label = "percent",
    ) { value ->
        Text(
            text = if (value == null) "—" else stringResource(R.string.percent, value),
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Badges(pod: PodComponent) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(pod.isCharging, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Badge(stringResource(R.string.charging), icon = true)
        }
        AnimatedVisibility(pod.isInEar, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Badge(stringResource(R.string.in_ear))
        }
        AnimatedVisibility(!pod.isConnected, enter = fadeIn(), exit = fadeOut()) {
            Badge(stringResource(R.string.absent))
        }
    }
}

@Composable
private fun Badge(text: String, icon: Boolean = false) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon) {
                Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ChargingIcon(modifier: Modifier) {
    Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, modifier = modifier, tint = MaterialTheme.colorScheme.tertiary)
}
