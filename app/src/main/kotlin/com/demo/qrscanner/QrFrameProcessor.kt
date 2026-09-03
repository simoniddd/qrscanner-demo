@file:Suppress("MagicNumber")
package com.demo.qrscanner

import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Rect
import com.google.mlkit.vision.barcode.common.Barcode

private class QrCodeSnapshot(
    var code: String? = null,
    var timestamp: Long = 0L
)

/**
 * Содержит stateful-логику обработки распознанных баркодов:
 * - выбор приоритетного кода (устойчивость/размер)
 * - проверка стабильности с задержкой
 * - захват и поворот кадра для кэша
 * - уведомления UI через коллбеки
 */
internal class QrFrameProcessor(
    private val previewView: PreviewView,
    private val onQrCodeScan: (String) -> Unit,
    private val onQrCodeDetect: (Rect) -> Unit,
    private val onQrCodeLost: () -> Unit,
    private val onFrameCapture: (android.graphics.Bitmap?) -> Unit,
    private val qrStabilityDelay: Long
) {
    private val recentQRCode = QrCodeSnapshot()
    private var lastFrameCaptureTime = 0L
    private val frameCaptureInterval = 500L

    fun processBarcodes(barcodes: List<Barcode>, imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val shouldCaptureFrame = currentTime - lastFrameCaptureTime >= frameCaptureInterval

        if (shouldCaptureFrame) {
            captureFrameForCache(imageProxy)
            lastFrameCaptureTime = currentTime
        }

        if (barcodes.isEmpty()) {
            processBarcodeNotFound()
            return
        }

        val barcode = choosePreferredBarcode(barcodes, recentQRCode.code)
        // Рамка статична — не отслеживаем позицию QR для максимальной скорости

        barcode.rawValue?.let { qrCode ->
            when {
                isQrCodeStable(qrCode, currentTime) -> {
                    handleStableQrCode(qrCode, imageProxy)
                }

                qrCode != recentQRCode.code -> {
                    updateStabilityState(qrCode, currentTime)
                }
            }
        }
    }

    private fun choosePreferredBarcode(barcodes: List<Barcode>, lastQrCode: String?): Barcode {
        lastQrCode?.let { last ->
            barcodes.firstOrNull { it.rawValue == last }?.let { return it }
        }
        return barcodes.maxByOrNull { b ->
            val box = b.boundingBox
            if (box != null) (box.width() * box.height()).toLong() else 0L
        } ?: barcodes.first()
    }

    private fun isQrCodeStable(qrCode: String, currentTime: Long): Boolean {
        return qrCode == recentQRCode.code &&
            currentTime - recentQRCode.timestamp >= qrStabilityDelay
    }

    private fun updateStabilityState(qrCode: String, currentTime: Long) {
        recentQRCode.code = qrCode
        recentQRCode.timestamp = currentTime
    }

    private fun resetStabilityState() {
        recentQRCode.code = null
        recentQRCode.timestamp = 0L
    }

    private fun handleStableQrCode(qrCode: String, imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()
            val rotatedBitmap = CameraUtils.rotateBitmap(bitmap, imageProxy)
            onFrameCapture(rotatedBitmap)
        } catch (_: Exception) {
        }
        resetStabilityState()
        onQrCodeScan(qrCode)
    }

    private fun processBarcodeNotFound() {
        resetStabilityState()
        onQrCodeLost()
    }

    /**
     * Захватывает кадр для кэша
     */
    private fun captureFrameForCache(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val rotatedBitmap = CameraUtils.rotateBitmap(bitmap, imageProxy)
        onFrameCapture(rotatedBitmap)
    }
}
