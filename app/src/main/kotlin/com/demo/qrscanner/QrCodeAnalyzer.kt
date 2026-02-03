package com.demo.qrscanner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import android.view.animation.DecelerateInterpolator
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Rect
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Задержка в миллисекундах для подтверждения стабильности QR-рамки
 * перед началом обработки скана.
 * Для банковского приложения рекомендуется 1500мс для защиты от случайных сканов.
 * Для демо уменьшено до 300мс для демонстрации скорости.
 */
private const val QR_STABILITY_DELAY_MS = 300L

/**
 * Базовый зум для исключения проблемы "не сканирует в упор".
 * Заставляет пользователя держать телефон чуть дальше, что помогает фокусировке.
 */
private const val DEFAULT_ZOOM_RATIO = 1.3f

/**
 * Порог изменения зума для исключения "дрожания" линзы.
 * Если разница между текущим и запрошенным зумом меньше порога — игнорируем.
 */
private const val ZOOM_CHANGE_THRESHOLD = 0.1f

/**
 * Длительность анимации плавного зума в миллисекундах.
 */
private const val ZOOM_ANIMATION_DURATION_MS = 300L

/**
 * Коррекция экспозиции для работы с яркими экранами (мониторы, смартфоны).
 * Отрицательное значение делает картинку темнее, "проявляя" QR на ярком фоне.
 * -2 — оптимально для большинства мониторов и освещённых помещений.
 */
private const val EXPOSURE_COMPENSATION_INDEX = -2

/**
 * Интервал между анализом кадров в миллисекундах.
 * 50мс = 20 FPS. Снижение частоты позволяет камере делать более чёткие снимки
 * при движении рук (меньше motion blur из-за увеличенной выдержки).
 */
private const val ANALYSIS_INTERVAL_MS = 50L

/**
 * Размер зоны интереса (ROI) как доля от меньшей стороны кадра.
 * 0.5 = 50% кадра. Включает padding 25% для "тихой зоны" вокруг QR.
 */
private const val ROI_SIZE_RATIO = 0.5f

/**
 * Коллбеки для реакций на события сканера/обработки кадра
 */
data class QrCodeAnalyzerCallbacks(
    val onQrCodeScan: (String) -> Unit,
    val onQrCodeDetect: (Rect) -> Unit,
    val onQrCodeLost: () -> Unit,
    val onFrameCapture: (Bitmap?) -> Unit
)

/**
 * Посредник между Compose и CameraX/MLKit:
 * - настраивает Preview и ImageAnalysis
 * - анализирует кадры и делегирует логику в [QrFrameProcessor]
 * - управляет ресурсами (executor, unbind)
 */
internal class QrCodeAnalyzer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val shouldStopAnalysisRef: () -> Boolean,
    private val callbacks: QrCodeAnalyzerCallbacks
) {
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    /** Текущий зум камеры */
    private var currentZoom = DEFAULT_ZOOM_RATIO

    /** Ссылка на камеру для управления зумом */
    private var camera: Camera? = null

    /** Сканер с поддержкой ZoomSuggestionOptions (инициализируется в start) */
    private var scanner: BarcodeScanner? = null

    /** Текущая анимация зума (для отмены при новом запросе) */
    private var zoomAnimator: ValueAnimator? = null

    /** Время последнего анализа кадра для троттлинга */
    private var lastAnalysisTime = 0L

    private val frameProcessor = QrFrameProcessor(
        previewView = previewView,
        onQrCodeScan = callbacks.onQrCodeScan,
        onQrCodeDetect = callbacks.onQrCodeDetect,
        onQrCodeLost = callbacks.onQrCodeLost,
        onFrameCapture = callbacks.onFrameCapture,
        qrStabilityDelay = QR_STABILITY_DELAY_MS
    )

    /** Запускает камеру и анализатор кадров */
    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = createPreview()
            val imageAnalysis = createImageAnalysis()

            cameraProvider.unbindAll()

            // Сохраняем ссылку на камеру для управления зумом
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

            preview.setSurfaceProvider(previewView.surfaceProvider)

            // Устанавливаем базовый зум 1.3x
            camera?.cameraControl?.setZoomRatio(DEFAULT_ZOOM_RATIO)

            // Снижаем экспозицию для борьбы с бликами от мониторов
            camera?.cameraControl?.setExposureCompensationIndex(EXPOSURE_COMPENSATION_INDEX)

            // Создаём сканер с поддержкой умного автозума
            val maxZoom = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 4f
            scanner = CameraUtils.createQrScannerWithZoom(
                onZoomRequested = { requestedZoom ->
                    // Игнорируем мелкие изменения для исключения "дрожания"
                    if (abs(requestedZoom - currentZoom) < ZOOM_CHANGE_THRESHOLD) {
                        return@createQrScannerWithZoom true
                    }
                    animateZoomTo(requestedZoom)
                    true
                },
                maxSupportedZoomRatio = maxZoom
            )
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Плавно анимирует зум камеры к целевому значению.
     * Отменяет предыдущую анимацию если она ещё выполняется.
     */
    private fun animateZoomTo(targetZoom: Float) {
        // Отменяем предыдущую анимацию
        zoomAnimator?.cancel()

        zoomAnimator = ValueAnimator.ofFloat(currentZoom, targetZoom).apply {
            duration = ZOOM_ANIMATION_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val animatedZoom = animator.animatedValue as Float
                camera?.cameraControl?.setZoomRatio(animatedZoom)
            }
            start()
        }
        currentZoom = targetZoom
    }

    /** Останавливает анализ и освобождает ресурсы */
    fun stop() {
        zoomAnimator?.cancel()
        zoomAnimator = null
        runCatching {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
        cameraExecutor.shutdown()
    }

    /**
     * Создаёт Preview с AspectRatio 4:3.
     * 4:3 — нативное соотношение большинства сенсоров камеры.
     * Использование 16:9 приводит к программной обрезке и потере чёткости.
     */
    private fun createPreview(): Preview {
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        return Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(previewView.display.rotation)
            .build()
    }

    /**
     * Создаёт ImageAnalysis с оптимальным разрешением 1080p и AspectRatio 4:3.
     * 1080p — баланс между качеством детекции высокоплотных QR и скоростью обработки.
     */
    private fun createImageAnalysis(): ImageAnalysis {
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1920, 1080),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        return ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(previewView.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(cameraExecutor, ::processImageFrame) }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageFrame(imageProxy: ImageProxy) {
        if (shouldStopAnalysisRef()) {
            imageProxy.close()
            return
        }

        // Троттлинг: пропускаем кадры чаще 20 FPS для уменьшения motion blur
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < ANALYSIS_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        lastAnalysisTime = now

        // Используем сканер с ZoomSuggestionOptions (или fallback на обычный)
        val activeScanner = scanner ?: CameraUtils.createQrOnlyScanner()

        imageProxy.image?.let { _ ->
            val rotation = imageProxy.imageInfo.rotationDegrees
            // ROI: анализируем только центральную зону для ускорения детекции
            val image = buildRoiInputImage(imageProxy, rotation)
            activeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    frameProcessor.processBarcodes(barcodes, imageProxy)
                }
                .addOnCompleteListener { imageProxy.close() }
        } ?: imageProxy.close()
    }

    /**
     * Создаёт InputImage из центральной зоны кадра (ROI).
     * Обрезка до 50% кадра ускоряет детекцию и снижает нагрузку на CPU.
     * Padding 25% гарантирует захват "тихой зоны" вокруг QR-кода.
     */
    private fun buildRoiInputImage(imageProxy: ImageProxy, rotation: Int): InputImage {
        val bitmap = imageProxy.toBitmap()
        val rotatedBitmap = CameraUtils.rotateBitmap(bitmap, imageProxy)

        val w = rotatedBitmap.width
        val h = rotatedBitmap.height
        val roiSize = (minOf(w, h) * ROI_SIZE_RATIO).toInt()
        val left = (w - roiSize) / 2
        val top = (h - roiSize) / 2

        val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, left, top, roiSize, roiSize)
        return InputImage.fromBitmap(croppedBitmap, 0) // rotation уже применён
    }
}
