package com.demo.qrscanner

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect

sealed interface QrScannerAction {
    data object RequestPermission : QrScannerAction
    data class OnQrScanned(val qrToken: String) : QrScannerAction
    data class OnQrCodeDetected(val bounds: Rect) : QrScannerAction
    data object OnQrCodeLost : QrScannerAction
    data object OnError : QrScannerAction
    data class OnFrameCaptured(val bitmap: Bitmap?) : QrScannerAction
    data object Retry : QrScannerAction
    data object Reset : QrScannerAction
    data object MoreInfoClick : QrScannerAction
    data object OnInfoOpened : QrScannerAction
    data object OnInfoClosed : QrScannerAction
    data object BackClick : QrScannerAction
    data object ScanAgain : QrScannerAction
}
