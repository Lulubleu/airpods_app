package com.airpodsapp.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.airpodsapp.R
import com.airpodsapp.bluetooth.PodsState
import com.airpodsapp.protocol.PodComponent
import com.airpodsapp.protocol.PodsModel
import com.airpodsapp.protocol.PodsStatus
import com.airpodsapp.protocol.aap.NoiseControlMode
import com.airpodsapp.ui.components.BatteryCard
import com.airpodsapp.ui.components.PodIllustration
import com.airpodsapp.ui.components.PodPart
import com.airpodsapp.ui.components.hasShortStem
import com.airpodsapp.ui.theme.PodsTheme
import kotlinx.coroutines.delay

/** The three things the home screen can show. */
private enum class HomeMode { PERMISSION, SEARCHING, STATUS }

@Composable
fun HomeScreen(
    state: PodsState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNoiseModeSelected: (NoiseControlMode) -> Unit = {},
) {
    val mode = when {
        !hasPermission -> HomeMode.PERMISSION
        state.isConnected && state.status != null -> HomeMode.STATUS
        else -> HomeMode.SEARCHING
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        AnimatedContent(
            targetState = mode,
            transitionSpec = { (fadeIn(tween(400)) + scaleIn(initialScale = 0.94f)) togetherWith fadeOut(tween(200)) },
            modifier = Modifier.fillMaxSize().padding(padding),
            label = "home-mode",
        ) { target ->
            when (target) {
                HomeMode.PERMISSION -> PermissionContent(onRequestPermission)
                HomeMode.SEARCHING -> SearchingContent()
                HomeMode.STATUS -> StatusContent(state, onNoiseModeSelected)
            }
        }
    }
}

@Composable
private fun StatusContent(state: PodsState, onNoiseModeSelected: (NoiseControlMode) -> Unit) {
    val status = state.status ?: return
    val shortStem = status.model.hasShortStem
    val live = state.aap != null

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Header(state.deviceName.orEmpty(), status.model, state.lastSeenMillis, live)
        Spacer(Modifier.height(12.dp))

        when (status) {
            is PodsStatus.Stereo -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BatteryCard(
                        stringResource(R.string.left), status.left, PodPart.LEFT,
                        Modifier.weight(1f).staggered(0), shortStem,
                    )
                    BatteryCard(
                        stringResource(R.string.right), status.right, PodPart.RIGHT,
                        Modifier.weight(1f).staggered(1), shortStem,
                    )
                }
                BatteryCard(
                    stringResource(R.string.case_label), status.case, PodPart.CASE,
                    Modifier.fillMaxWidth().staggered(2), horizontal = true,
                )
            }
            is PodsStatus.Single -> BatteryCard(
                stringResource(R.string.battery), status.battery, PodPart.HEADSET,
                Modifier.fillMaxWidth().staggered(0),
            )
        }

        state.noiseMode?.let { mode ->
            NoiseControlCard(
                current = mode,
                showAdaptive = status.model.supportsAdaptive || mode == NoiseControlMode.ADAPTIVE,
                onSelect = onNoiseModeSelected,
                modifier = Modifier.staggered(3),
            )
        }

        if (!live) {
            Text(
                text = stringResource(R.string.precision_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

private val PodsModel.supportsAdaptive: Boolean
    get() = this == PodsModel.AIRPODS_PRO_2 || this == PodsModel.AIRPODS_PRO_3

/** Noise-control selector, shown only when the AirPods report a mode over AAP. */
@Composable
private fun NoiseControlCard(
    current: NoiseControlMode,
    showAdaptive: Boolean,
    onSelect: (NoiseControlMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = buildList {
        add(NoiseControlMode.OFF to R.string.noise_off)
        add(NoiseControlMode.TRANSPARENCY to R.string.noise_transparency)
        if (showAdaptive) add(NoiseControlMode.ADAPTIVE to R.string.noise_adaptive)
        add(NoiseControlMode.NOISE_CANCELLATION to R.string.noise_anc)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(R.string.noise_control),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 2-column grid of tiles: long French labels don't fit a 4-segment row.
            modes.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (mode, label) ->
                        ModeTile(stringResource(label), selected = mode == current, Modifier.weight(1f)) { onSelect(mode) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ModeTile(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHighest, label = "tile")
    val content by animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, label = "tile-content")
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun Header(name: String, model: PodsModel, lastSeenMillis: Long, live: Boolean) {
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PulsingDot()
                    Text(
                        stringResource(R.string.connected),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text(
                text = model.displayName + " · " +
                    if (live) stringResource(R.string.live) else relativeTime(lastSeenMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "à l'instant", "il y a 12 s"…, refreshed every second. */
@Composable
private fun relativeTime(millis: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val seconds = ((now - millis) / 1000).coerceAtLeast(0)
    return when {
        seconds < 5 -> stringResource(R.string.updated_now)
        seconds < 60 -> stringResource(R.string.updated_seconds, seconds.toInt())
        else -> stringResource(R.string.updated_minutes, (seconds / 60).toInt())
    }
}

@Composable
private fun PulsingDot() {
    val alpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot-alpha",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(8.dp)) { drawCircle(color, alpha = alpha) }
}

/** Searching: earbuds with sonar rings expanding behind them. */
@Composable
private fun SearchingContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            SonarRings(Modifier.fillMaxSize())
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(112.dp)) {
                Row(Modifier.padding(20.dp)) {
                    val color = MaterialTheme.colorScheme.onPrimaryContainer
                    PodIllustration(PodPart.LEFT, color, Modifier.weight(1f).fillMaxSize())
                    PodIllustration(PodPart.RIGHT, color, Modifier.weight(1f).fillMaxSize())
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.searching),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.searching_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SonarRings(modifier: Modifier) {
    val progress by rememberInfiniteTransition(label = "sonar").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "sonar-progress",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val maxRadius = size.minDimension / 2
        val minRadius = maxRadius * 0.5f
        repeat(3) { i ->
            val t = (progress + i / 3f) % 1f
            drawCircle(
                color = color,
                radius = minRadius + (maxRadius - minRadius) * t,
                alpha = (1f - t) * 0.5f,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun PermissionContent(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(88.dp)) {
                    PodIllustration(
                        PodPart.CASE,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        Modifier.fillMaxSize().padding(12.dp),
                    )
                }
                Text(
                    stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.permission_rationale),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.permission_grant), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Cards slide up and fade in one after another when the status first appears. */
@Composable
private fun Modifier.staggered(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 90L)
        progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 48.dp.toPx()
    }
}

private val previewState = PodsState(
    deviceName = "Mes AirPods Pro",
    status = PodsStatus.Stereo(
        model = PodsModel.AIRPODS_PRO_2,
        left = PodComponent(8, isCharging = false, isInEar = true),
        right = PodComponent(7, isCharging = false, isInEar = true),
        case = PodComponent(4, isCharging = true),
    ),
    lastSeenMillis = System.currentTimeMillis(),
)

@Preview(showBackground = true)
@Composable
private fun StatusPreview() {
    PodsTheme { HomeScreen(previewState, hasPermission = true, onRequestPermission = {}) }
}

@Preview(showBackground = true)
@Composable
private fun SearchingPreview() {
    PodsTheme { HomeScreen(PodsState(), hasPermission = true, onRequestPermission = {}) }
}

@Preview(showBackground = true)
@Composable
private fun PermissionPreview() {
    PodsTheme { HomeScreen(PodsState(), hasPermission = false, onRequestPermission = {}) }
}
