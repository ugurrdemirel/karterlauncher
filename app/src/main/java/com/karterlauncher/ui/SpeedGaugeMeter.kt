package com.karterlauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.karterlauncher.ui.theme.LauncherMotion
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Gauge arc scale upper bound (needle & fill clamp to this span). */
private const val GaugeMaxKmhDisplay = 180f

private const val DegStartArc = 180f
private const val DegSweepArc = -180f

private fun Color.toArgb8888(): Int {
    val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return android.graphics.Color.argb(a, r, g, b)
}

@Composable
fun SpeedSemiGaugeMeter(
    speedKmh: Float,
    enabled: Boolean,
    trackColor: Color,
    fillBrush: Brush,
    needleBaseColor: Color,
    tickColor: Color,
    modifier: Modifier = Modifier,
    maxScaleKmh: Float = GaugeMaxKmhDisplay,
) {
    val targetFrac =
        remember(speedKmh, maxScaleKmh, enabled) {
            if (!enabled) 0f else (speedKmh / maxScaleKmh).coerceIn(0f, 1f)
        }
    val frac by animateFloatAsState(
        targetValue = targetFrac,
        animationSpec = LauncherMotion.GaugeSpring,
        label = "gaugeNeedle",
    )
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pivot = Offset(w * 0.5f, h * 0.93f)
        val radiusOuter = min(w, h * 1.95f) * 0.44f
        val arcStroke = 13.dp.toPx()
        val topLeftArc = Offset(pivot.x - radiusOuter, pivot.y - radiusOuter)
        val oval = Size(radiusOuter * 2f, radiusOuter * 2f)

        drawArc(
            color = trackColor,
            startAngle = DegStartArc,
            sweepAngle = DegSweepArc,
            useCenter = false,
            topLeft = topLeftArc,
            size = oval,
            style = Stroke(width = arcStroke, cap = StrokeCap.Round),
        )

        if (enabled && frac > 1e-3f) {
            drawArc(
                brush = fillBrush,
                startAngle = DegStartArc,
                sweepAngle = DegSweepArc * frac,
                useCenter = false,
                topLeft = topLeftArc,
                size = oval,
                style = Stroke(width = arcStroke, cap = StrokeCap.Round),
            )
        }

        val needleLen = radiusOuter * 0.78f
        val theta = PI * (1.0 - frac.toDouble())
        val nx = pivot.x + (needleLen * cos(theta)).toFloat()
        val ny = pivot.y - (needleLen * sin(theta)).toFloat()
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(needleBaseColor.copy(alpha = 0.35f), needleBaseColor),
                start = pivot,
                end = Offset(nx, ny),
            ),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round,
            start = pivot,
            end = Offset(nx, ny),
            alpha = if (enabled) 1f else 0.38f,
        )
        drawCircle(
            color = needleBaseColor,
            radius = 5.dp.toPx(),
            center = pivot,
            alpha = if (enabled) 1f else 0.5f,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = 1.75.dp.toPx(),
            center = pivot,
            alpha = if (enabled) 0.9f else 0.35f,
        )

        drawSpeedDialTicks(
            pivot = pivot,
            ringRadius = radiusOuter - arcStroke * 0.85f,
            tickColor = tickColor,
            maxScaleKmh = maxScaleKmh,
        )
    }
}

private fun DrawScope.drawSpeedDialTicks(
    pivot: Offset,
    ringRadius: Float,
    tickColor: Color,
    maxScaleKmh: Float,
) {
    val step = 10
    val max = maxScaleKmh.roundToInt().coerceAtLeast(step)
    for (kmInt in 0..max step step) {
        val km = kmInt.toDouble()
        val t = km / maxScaleKmh.toDouble().coerceAtLeast(1.0)
        val angle = PI * (1.0 - t)
        val dirX = cos(angle).toFloat()
        val dirY = -sin(angle).toFloat()
        val dir = Offset(dirX, dirY)
        val isLabeled = kmInt == 0 || kmInt == 90 || kmInt == max
        val isStrong = kmInt % 45 == 0
        val tickLen = when {
            isStrong -> 8.dp.toPx()
            else -> 5.dp.toPx()
        }
        val inset = -2.dp.toPx()
        val innerMult = ringRadius - tickLen + inset
        val outerMult = ringRadius + inset
        val inner = Offset(pivot.x + dir.x * innerMult, pivot.y + dir.y * innerMult)
        val outer = Offset(pivot.x + dir.x * outerMult, pivot.y + dir.y * outerMult)
        val alphaLine = when {
            isStrong -> 0.9f
            isLabeled -> 0.72f
            else -> 0.45f
        }
        val strokeW = when {
            isStrong -> 2.dp.toPx()
            else -> 1.2.dp.toPx()
        }
        drawLine(
            color = tickColor.copy(alpha = alphaLine),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
            start = inner,
            end = outer,
        )
        if (isLabeled) {
            drawScaleLabel(
                center = pivot,
                dir = dir,
                radiusText = ringRadius - 26.dp.toPx(),
                text = kmInt.toString(),
                color = tickColor.copy(alpha = 0.88f),
            )
        }
    }
}

private fun DrawScope.drawScaleLabel(
    center: Offset,
    dir: Offset,
    radiusText: Float,
    text: String,
    color: Color,
) {
    val p = Offset(
        center.x + dir.x * radiusText,
        center.y + dir.y * radiusText - 6.dp.toPx(),
    )
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 10.dp.toPx()
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        this.color = color.toArgb8888()
    }
    drawContext.canvas.nativeCanvas.drawText(
        text,
        p.x,
        p.y,
        paint,
    )
}
