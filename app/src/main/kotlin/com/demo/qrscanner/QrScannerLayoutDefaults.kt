package com.demo.qrscanner

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object QrScannerLayoutDefaults {
    private const val boundsAnimationDurationMillis: Int = 300

    const val qrBoundsAnimationLabel: String = "QrBounds"

    val standardFrameSize: Dp = 244.dp

    val cornerSize: Dp = 52.dp

    val frameStrokeWidth: Dp = 3.dp

    const val frameRadiusDivider: Float = 8f

    val frameStrokeCap: StrokeCap = StrokeCap.Round

    val frameStrokeJoin: StrokeJoin = StrokeJoin.Round

    private val boundsEasing: Easing = LinearEasing

    val frameStrokeColor: Color = Color(0xFFEEFE6D)

    val contentColor: Color = Color.White

    fun qrBoundsAnimation(
        durationMillis: Int = boundsAnimationDurationMillis
    ): AnimationSpec<Rect> {
        return tween(
            durationMillis = durationMillis,
            easing = boundsEasing
        )
    }
}
