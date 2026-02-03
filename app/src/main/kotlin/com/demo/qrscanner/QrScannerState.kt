package com.demo.qrscanner

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect

@Immutable
data class QrScannerState(
    val isLoading: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val lastFrameBitmap: Bitmap? = null,
    val qrCodeBounds: Rect? = null,
    val isQrCodeDetected: Boolean = false,
    val isInfoShowing: Boolean = false,
    val isErrorShowing: Boolean = false,
    val scannedCode: String? = null
) {
    val shouldStopAnalysis: Boolean
        get() = isLoading || isInfoShowing || isErrorShowing || scannedCode != null
}
