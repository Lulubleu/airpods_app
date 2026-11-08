package com.airpodsapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import com.airpodsapp.protocol.PodsModel

/** What to draw inside a battery ring. */
enum class PodPart { LEFT, RIGHT, CASE, HEADSET }

/** Pro models have a shorter stem. */
val PodsModel.hasShortStem: Boolean
    get() = this == PodsModel.AIRPODS_PRO || this == PodsModel.AIRPODS_PRO_2 || this == PodsModel.AIRPODS_PRO_3

/**
 * Minimal flat illustration of an earbud, the case or a headset, drawn with shapes
 * so it takes the theme color and stays sharp at any size.
 */
@Composable
fun PodIllustration(part: PodPart, color: Color, modifier: Modifier = Modifier, shortStem: Boolean = false) {
    Canvas(modifier) {
        when (part) {
            PodPart.LEFT -> drawBud(color, shortStem)
            PodPart.RIGHT -> scale(scaleX = -1f, scaleY = 1f) { drawBud(color, shortStem) }
            PodPart.CASE -> drawCase(color)
            PodPart.HEADSET -> drawHeadset(color)
        }
    }
}

private fun DrawScope.drawBud(color: Color, shortStem: Boolean) {
    val w = size.minDimension
    val headRadius = w * 0.24f
    // Length of the stem below the head; the whole bud is vertically centred.
    val stemBelow = w * (if (shortStem) 0.16f else 0.3f)
    val head = Offset(center.x - w * 0.08f, center.y - w * (if (shortStem) 0.07f else 0.14f))
    val stemWidth = w * 0.14f
    drawRoundRect(
        color = color,
        topLeft = Offset(head.x + headRadius * 0.25f, head.y),
        size = Size(stemWidth, headRadius + stemBelow),
        cornerRadius = CornerRadius(stemWidth / 2),
    )
    drawCircle(color, headRadius, head)
}

private fun DrawScope.drawCase(color: Color) {
    val w = size.minDimension
    val caseSize = Size(w * 0.64f, w * 0.5f)
    val topLeft = Offset(center.x - caseSize.width / 2, center.y - caseSize.height / 2)
    drawRoundRect(color, topLeft, caseSize, CornerRadius(w * 0.16f))
    // Lid seam.
    val seamY = topLeft.y + caseSize.height * 0.32f
    drawLine(
        color = Color.Black.copy(alpha = 0.18f),
        start = Offset(topLeft.x, seamY),
        end = Offset(topLeft.x + caseSize.width, seamY),
        strokeWidth = w * 0.025f,
    )
}

private fun DrawScope.drawHeadset(color: Color) {
    val w = size.minDimension
    val band = w * 0.08f
    val arcSize = Size(w * 0.56f, w * 0.56f)
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - arcSize.width / 2, center.y - arcSize.height * 0.55f),
        size = arcSize,
        style = Stroke(band, cap = StrokeCap.Round),
    )
    val cup = Size(w * 0.18f, w * 0.3f)
    for (x in listOf(center.x - arcSize.width / 2 - cup.width / 2, center.x + arcSize.width / 2 - cup.width / 2)) {
        drawRoundRect(color, Offset(x, center.y - w * 0.08f), cup, CornerRadius(cup.width / 2))
    }
}
