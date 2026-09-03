package com.demo.qrscanner

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import androidx.compose.ui.geometry.Rect as ComposeRect

/**
 * Stateless-утилиты для камеры/сканера:
 * - создание QR-only сканера
 * - трансформация координат
 * - поворот bitmap
 */
internal object CameraUtils {
    private const val PORTRAIT_ROTATION_90 = 90
    private const val PORTRAIT_ROTATION_270 = 270

    fun createQrOnlyScanner(): BarcodeScanner {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        return BarcodeScanning.getClient(options)
    }

    /**
     * Создаёт сканер с поддержкой ZoomSuggestionOptions.
     * Когда ML Kit видит QR, но не может его считать из-за малого размера,
     * он вызывает [onZoomRequested] с рекомендуемым zoomRatio.
     */
    fun createQrScannerWithZoom(
        onZoomRequested: (Float) -> Boolean,
        maxSupportedZoomRatio: Float
    ): BarcodeScanner {
        val zoomOptions = ZoomSuggestionOptions.Builder(onZoomRequested)
            .setMaxSupportedZoomRatio(maxSupportedZoomRatio)
            .build()

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .setZoomSuggestionOptions(zoomOptions)
            .build()
        return BarcodeScanning.getClient(options)
    }

    fun transformBoundingBox(
        box: Rect,
        imageProxy: ImageProxy,
        previewView: PreviewView
    ): ComposeRect {
        val (previewWidth, previewHeight) = with(previewView) { width.toFloat() to height.toFloat() }
        val (imageWidth, imageHeight) = with(imageProxy) { width.toFloat() to height.toFloat() }
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val (rotatedImageWidth, rotatedImageHeight) =
            if (rotationDegrees == PORTRAIT_ROTATION_90 ||
                rotationDegrees == PORTRAIT_ROTATION_270
            ) {
                imageHeight to imageWidth
            } else {
                imageWidth to imageHeight
            }

        val scaleX = previewWidth / rotatedImageWidth
        val scaleY = previewHeight / rotatedImageHeight
        val scaleFactor = maxOf(scaleX, scaleY)

        val offsetX = (previewWidth - rotatedImageWidth * scaleFactor) / 2
        val offsetY = (previewHeight - rotatedImageHeight * scaleFactor) / 2
        val left = box.left * scaleFactor + offsetX
        val top = box.top * scaleFactor + offsetY
        val right = box.right * scaleFactor + offsetX
        val bottom = box.bottom * scaleFactor + offsetY

        return ComposeRect(left, top, right, bottom)
    }

    fun rotateBitmap(bitmap: Bitmap, imageProxy: ImageProxy): Bitmap {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees == 0) return bitmap

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }
}
