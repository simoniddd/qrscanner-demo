package com.demo.qrscanner

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun QrScannerScreen(
    viewModel: QrScannerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    LaunchedEffect(Unit) {
        if (!state.hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LifecycleEventEffect(event = Lifecycle.Event.ON_RESUME) {
        if (state.isInfoShowing) {
            viewModel.onAction(QrScannerAction.OnInfoClosed)
        } else {
            if (state.isLoading) {
                viewModel.onAction(QrScannerAction.OnError)
            } else if (state.scannedCode == null) {
                viewModel.onAction(QrScannerAction.Reset)
            }
        }
    }

    BackHandler {
        viewModel.onAction(QrScannerAction.BackClick)
    }

    QrScannerLayout(
        state = state,
        onAction = viewModel::onAction
    )
}

@Composable
private fun QrScannerLayout(
    state: QrScannerState,
    onAction: (QrScannerAction) -> Unit
) {
    Scaffold(
        topBar = {
            QrScannerToolbar(
                modifier = Modifier.statusBarsPadding(),
                title = stringResource(R.string.qr_scanner_title)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when {
                state.scannedCode != null -> {
                    ScannedResultScreen(
                        code = state.scannedCode,
                        onScanAgain = { onAction(QrScannerAction.ScanAgain) }
                    )
                }

                state.isInfoShowing -> {
                    CachedFrameWithLoader(
                        bitmap = state.lastFrameBitmap,
                        showLoader = false
                    )
                }

                state.isErrorShowing -> {
                    CachedFrameWithLoader(
                        bitmap = state.lastFrameBitmap,
                        showLoader = false
                    )
                }

                state.isLoading -> {
                    CachedFrameWithLoader(bitmap = state.lastFrameBitmap, showLoader = true)
                }

                state.hasCameraPermission -> {
                    QrScannerCameraView(
                        state = state,
                        onAction = onAction
                    )
                }

                else -> {
                    PermissionRequest(
                        onRequestPermission = { onAction(QrScannerAction.RequestPermission) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannedResultScreen(
    code: String,
    onScanAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "QR-код отсканирован:",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = code,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onScanAgain) {
            Text("Сканировать ещё")
        }
    }
}

@Composable
private fun PermissionRequest(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.camera_permission_required),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}

@Composable
private fun QrScannerCameraView(
    state: QrScannerState,
    onAction: (QrScannerAction) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            onQrCodeScan = { qrToken -> onAction(QrScannerAction.OnQrScanned(qrToken)) },
            onQrCodeDetect = { bounds -> onAction(QrScannerAction.OnQrCodeDetected(bounds)) },
            onQrCodeLost = { onAction(QrScannerAction.OnQrCodeLost) },
            onFrameCapture = { bitmap -> onAction(QrScannerAction.OnFrameCaptured(bitmap)) },
            shouldStopAnalysis = state.shouldStopAnalysis
        )
        QrScannerOverlay(
            onMoreInfoClick = { onAction(QrScannerAction.MoreInfoClick) },
            qrCodeBounds = state.qrCodeBounds,
            isQrCodeDetected = state.isQrCodeDetected
        )
    }
}

@Composable
private fun CameraPreview(
    onQrCodeScan: (String) -> Unit,
    onQrCodeDetect: (Rect) -> Unit,
    onQrCodeLost: () -> Unit,
    onFrameCapture: (Bitmap?) -> Unit,
    shouldStopAnalysis: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val updatedShouldStopAnalysis by rememberUpdatedState(shouldStopAnalysis)

    DisposableEffect(lifecycleOwner) {
        val analyzer = QrCodeAnalyzer(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            shouldStopAnalysisRef = { updatedShouldStopAnalysis },
            callbacks = QrCodeAnalyzerCallbacks(
                onQrCodeScan = onQrCodeScan,
                onQrCodeDetect = onQrCodeDetect,
                onQrCodeLost = onQrCodeLost,
                onFrameCapture = onFrameCapture
            )
        )
        analyzer.start()

        onDispose { analyzer.stop() }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun CachedFrameWithLoader(
    bitmap: Bitmap?,
    showLoader: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
            )
        }
        if (showLoader) {
            QrScannerLoadingOverlay()
            CircularProgressIndicator(
                color = Color.White
            )
        }
    }
}

@Composable
private fun QrScannerOverlay(
    onMoreInfoClick: () -> Unit,
    qrCodeBounds: Rect?,
    isQrCodeDetected: Boolean
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = this.maxWidth
        val screenHeight = this.maxHeight
        val density = LocalDensity.current
        val defaultBounds = remember(screenWidth, screenHeight, density) {
            calculateDefaultBounds(
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                density = density
            )
        }
        // Dark overlay
        QrScannerDarkOverlay()
        // Animated frame
        AnimatedScanningFrame(
            targetBounds = if (isQrCodeDetected && qrCodeBounds != null) qrCodeBounds else defaultBounds
        )
        // Instruction text
        QrScannerInstructionText(
            text = stringResource(R.string.qr_scanner_instruction),
            modifier = Modifier.align(Alignment.TopCenter)
        )
        // More info button
        QrScannerMoreInfoButton(
            text = "Подробнее",
            onClick = onMoreInfoClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
        )
    }
}

private fun calculateDefaultBounds(
    screenWidth: Dp,
    screenHeight: Dp,
    density: Density
): Rect {
    val screenWidthPx = with(density) { screenWidth.toPx() }
    val screenHeightPx = with(density) { screenHeight.toPx() }
    val standardFrameSizePx = with(density) { QrScannerLayoutDefaults.standardFrameSize.toPx() }
    return Rect(
        left = (screenWidthPx - standardFrameSizePx) / 2,
        top = (screenHeightPx - standardFrameSizePx) / 2,
        right = (screenWidthPx + standardFrameSizePx) / 2,
        bottom = (screenHeightPx + standardFrameSizePx) / 2
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrScannerToolbar(
    title: String,
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                color = QrScannerLayoutDefaults.contentColor
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun QrScannerLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
    )
}

@Composable
private fun QrScannerDarkOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    )
}

@Composable
private fun QrScannerInstructionText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = QrScannerLayoutDefaults.contentColor,
        modifier = modifier.padding(top = 112.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun QrScannerMoreInfoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .size(width = 88.dp, height = 96.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("?", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = QrScannerLayoutDefaults.contentColor
        )
    }
}
