package com.demo.qrscanner

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class QrScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(QrScannerState())
    val state: StateFlow<QrScannerState> = _state.asStateFlow()

    private var lastQrBounds: Rect? = null
    private var isProcessingQr = false

    init {
        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        val hasPermission = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            _state.update { it.copy(hasCameraPermission = true) }
        }
    }

    fun onAction(action: QrScannerAction) {
        when (action) {
            is QrScannerAction.OnQrCodeDetected -> handleQrCodeDetected(action.bounds)
            QrScannerAction.OnQrCodeLost -> handleQrCodeLost()
            is QrScannerAction.OnQrScanned -> handleQrScanned(action.qrToken)
            is QrScannerAction.OnFrameCaptured -> onFrameCaptured(action.bitmap)
            QrScannerAction.Retry -> handleRetry()
            QrScannerAction.MoreInfoClick -> handleMoreInfoClick()
            QrScannerAction.RequestPermission -> { /* handled externally */ }
            QrScannerAction.OnInfoOpened -> setInfoShowing(true)
            QrScannerAction.OnInfoClosed -> setInfoShowing(false)
            QrScannerAction.OnError -> handleScanError()
            QrScannerAction.Reset -> resetScannerState()
            QrScannerAction.BackClick -> { /* handled externally */ }
            QrScannerAction.ScanAgain -> handleScanAgain()
        }
    }

    fun onPermissionGranted() {
        _state.update { it.copy(hasCameraPermission = true) }
    }

    fun onPermissionDenied() {
        _state.update { it.copy(hasCameraPermission = false) }
    }

    fun resetScannerState() {
        isProcessingQr = false
        lastQrBounds = null
        _state.update {
            QrScannerState(hasCameraPermission = it.hasCameraPermission)
        }
    }

    private fun handleQrCodeDetected(bounds: Rect) {
        val currentBounds = bounds
        val previousBounds = lastQrBounds
        val isBoundsChanged = previousBounds?.let { !isBoundsStable(it, currentBounds) } != false

        if (isBoundsChanged || !_state.value.isQrCodeDetected) {
            lastQrBounds = currentBounds
            _state.update {
                it.copy(
                    qrCodeBounds = currentBounds,
                    isQrCodeDetected = true
                )
            }
        }
    }

    private fun handleQrCodeLost() {
        lastQrBounds = null
        if (_state.value.isQrCodeDetected || _state.value.qrCodeBounds != null) {
            _state.update {
                it.copy(
                    qrCodeBounds = null,
                    isQrCodeDetected = false
                )
            }
        }
    }

    private fun handleQrScanned(qrToken: String) {
        if (_state.value.isLoading || isProcessingQr) {
            return
        }

        isProcessingQr = true
        viewModelScope.launch {
            delay(ANIMATION_DELAY_MS)
            _state.update { it.copy(isLoading = true) }

            // Simulate processing delay
            delay(500L)

            _state.update {
                it.copy(
                    isLoading = false,
                    scannedCode = qrToken
                )
            }
        }
    }

    private fun onFrameCaptured(bitmap: Bitmap?) {
        _state.update { it.copy(lastFrameBitmap = bitmap) }
    }

    private fun handleRetry() {
        isProcessingQr = false
        _state.update { it.copy(isLoading = false) }
    }

    private fun handleMoreInfoClick() {
        setInfoShowing(true)
    }

    private fun handleScanError() {
        _state.update { it.copy(isLoading = false) }
        setErrorShowing(true)
    }

    private fun handleScanAgain() {
        isProcessingQr = false
        _state.update {
            it.copy(
                scannedCode = null,
                isLoading = false,
                isErrorShowing = false,
                qrCodeBounds = null,
                isQrCodeDetected = false
            )
        }
    }

    private fun setInfoShowing(isShowing: Boolean) {
        _state.update { it.copy(isInfoShowing = isShowing) }
    }

    private fun setErrorShowing(isShowing: Boolean) {
        _state.update { it.copy(isErrorShowing = isShowing) }
    }

    private fun isBoundsStable(previous: Rect, current: Rect): Boolean {
        val tolerance = BOUNDS_TOLERANCE
        return abs(previous.left - current.left) <= tolerance &&
            abs(previous.top - current.top) <= tolerance &&
            abs(previous.right - current.right) <= tolerance &&
            abs(previous.bottom - current.bottom) <= tolerance
    }

    private companion object {
        const val ANIMATION_DELAY_MS = 300L
        const val BOUNDS_TOLERANCE = 20f
    }
}
