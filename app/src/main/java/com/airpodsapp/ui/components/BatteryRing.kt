package com.airpodsapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Circular battery gauge. The arc fills with a spring when [level] changes (and from 0 when
 * it first appears), turns to the error color when [low] and breathes while [charging].
 *
 * @param level 0..1, or null when unknown (only the track is drawn).
 */
@Composable
fun BatteryRing(
    level: Float?,
    charging: Boolean,
    low: Boolean,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val sweep by animateFloatAsState(
        targetValue = (level ?: 0f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "battery-sweep",
    )
    val arcColor by animateColorAsState(
        targetValue = when {
            low && !charging -> colors.error
            charging -> colors.tertiary
            else -> colors.primary
        },
        label = "battery-color",
    )
    val glow = if (charging) {
        val transition = rememberInfiniteTransition(label = "charging")
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
            label = "charging-alpha",
        ).value
    } else {
        1f
    }
    val track = colors.surfaceContainerHighest

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = size.copy(width = size.width - stroke.width, height = size.height - stroke.width)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = stroke)
            if (sweep > 0f) {
                drawArc(arcColor.copy(alpha = glow), -90f, 360f * sweep, false, topLeft, arcSize, style = stroke)
            }
        }
        content()
    }
}
