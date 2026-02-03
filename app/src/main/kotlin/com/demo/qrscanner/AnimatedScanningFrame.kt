package com.demo.qrscanner

import androidx.compose.animation.core.animateRectAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
fun AnimatedScanningFrame(targetBounds: Rect) {
    val animatedBounds by animateRectAsState(
        targetValue = targetBounds,
        label = QrScannerLayoutDefaults.qrBoundsAnimationLabel,
        animationSpec = QrScannerLayoutDefaults.qrBoundsAnimation()
    )
    val density = LocalDensity.current
    val strokeColor = QrScannerLayoutDefaults.frameStrokeColor

    Canvas(
        modifier = Modifier
            .size(
                width = with(density) { animatedBounds.width.toDp() },
                height = with(density) { animatedBounds.height.toDp() }
            )
            .offset {
                IntOffset(
                    x = animatedBounds.left.roundToInt(),
                    y = animatedBounds.top.roundToInt()
                )
            }
    ) {
        val strokeWidthPx = QrScannerLayoutDefaults.frameStrokeWidth.toPx()
        val side = minOf(size.width, size.height)

        val cornerRadius: Dp = with(density) { (side / QrScannerLayoutDefaults.frameRadiusDivider).toDp() }
        val radiusPx = cornerRadius.toPx()

        val desiredCorner = QrScannerLayoutDefaults.cornerSize.toPx()
        val maxCorner = (side / 2f - radiusPx).coerceAtLeast(0f)
        val cornerLenPx = desiredCorner.coerceAtMost(maxCorner)

        val path = createScanningFramePath(
            size = size,
            cornerLenPx = cornerLenPx,
            radiusPx = radiusPx
        )

        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(
                width = strokeWidthPx,
                cap = QrScannerLayoutDefaults.frameStrokeCap,
                join = QrScannerLayoutDefaults.frameStrokeJoin
            )
        )
    }
}

private fun createScanningFramePath(
    size: Size,
    cornerLenPx: Float,
    radiusPx: Float
): Path {
    return Path().apply {
        // Top-left corner
        moveTo(0f, cornerLenPx)
        lineTo(0f, radiusPx)
        arcTo(
            rect = Rect(0f, 0f, 2 * radiusPx, 2 * radiusPx),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        lineTo(cornerLenPx, 0f)

        // Top-right corner
        moveTo(size.width - cornerLenPx, 0f)
        lineTo(size.width - radiusPx, 0f)
        arcTo(
            rect = Rect(size.width - 2 * radiusPx, 0f, size.width, 2 * radiusPx),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        lineTo(size.width, cornerLenPx)

        // Bottom-right corner
        moveTo(size.width, size.height - cornerLenPx)
        lineTo(size.width, size.height - radiusPx)
        arcTo(
            rect = Rect(
                size.width - 2 * radiusPx,
                size.height - 2 * radiusPx,
                size.width,
                size.height
            ),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        lineTo(size.width - cornerLenPx, size.height)

        // Bottom-left corner
        moveTo(cornerLenPx, size.height)
        lineTo(radiusPx, size.height)
        arcTo(
            rect = Rect(0f, size.height - 2 * radiusPx, 2 * radiusPx, size.height),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
        lineTo(0f, size.height - cornerLenPx)
    }
}
