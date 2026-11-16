package com.focusstudy.poc

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The مُنظّم mark: two rounded hooks (white left, teal right) enclosing a teal cube —
 * order and growth meeting structure. Same geometry as ic_launcher_foreground.xml,
 * the monochrome layer and the notification icon, so the identity stays one shape.
 */
@Composable
fun FocusLogo(modifier: Modifier = Modifier, size: Dp = 56.dp, showTile: Boolean = true) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        if (showTile) {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(FocusColors.Navy, Color(0xFF0C1B2B))),
                cornerRadius = CornerRadius(w * 0.28f, h * 0.28f),
                size = Size(w, h)
            )
        }

        val strokeWidth = w * (12f / 108f)
        val inset = if (showTile) 0f else 0.1f
        val scale = 1f - inset * 2
        fun sx(v: Float) = (inset + v / 108f * scale) * w
        fun sy(v: Float) = (inset + v / 108f * scale) * h

        val left = Path().apply {
            moveTo(sx(37f), sy(47f))
            lineTo(sx(37f), sy(63f))
            cubicTo(sx(37f), sy(70f), sx(40f), sy(72.5f), sx(44f), sy(76.5f))
            lineTo(sx(53f), sy(85f))
        }
        drawPath(
            path = left,
            color = Color.White,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val right = Path().apply {
            moveTo(sx(71f), sy(47f))
            lineTo(sx(71f), sy(63f))
            cubicTo(sx(71f), sy(70f), sx(68f), sy(72.5f), sx(64f), sy(76.5f))
            lineTo(sx(55f), sy(85f))
        }
        drawPath(
            path = right,
            color = FocusColors.Teal,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val cube = Path().apply {
            moveTo(sx(54f), sy(16f))
            lineTo(sx(74f), sy(29f))
            lineTo(sx(74f), sy(33f))
            lineTo(sx(54f), sy(46f))
            lineTo(sx(34f), sy(33f))
            lineTo(sx(34f), sy(29f))
            close()
        }
        drawPath(path = cube, color = FocusColors.Teal)
        drawPath(
            path = cube,
            color = FocusColors.Teal,
            style = Stroke(width = w * (5f / 108f), join = StrokeJoin.Round)
        )
    }
}

/** Deep navy background with one drifting teal glow; matches the brand sheet. */
@Composable
fun BrandBackground(animate: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val drift = if (animate) {
        val transition = rememberInfiniteTransition(label = "background")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
            label = "drift"
        )
        value
    } else {
        // Reduced motion, or a focus session in progress: one calm frame, no animation work.
        0.35f
    }

    Box(modifier = Modifier.fillMaxSize().background(FocusColors.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.9f
            val tealCenter = Offset(size.width * (0.15f + 0.2f * drift), -radius * 0.2f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(FocusColors.Teal.copy(alpha = 0.22f), Color.Transparent),
                    center = tealCenter,
                    radius = radius
                ),
                radius = radius,
                center = tealCenter
            )
            val navyCenter = Offset(size.width * (0.9f - 0.15f * drift), size.height * 0.8f)
            val navyRadius = size.minDimension * 0.8f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(FocusColors.BlueDeep.copy(alpha = 0.22f), Color.Transparent),
                    center = navyCenter,
                    radius = navyRadius
                ),
                radius = navyRadius,
                center = navyCenter
            )
        }
        content()
    }
}
