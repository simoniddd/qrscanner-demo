# QR Scanner Demo

A high-performance Android QR code scanning application built with CameraX and ML Kit. This project demonstrates advanced camera optimizations designed for real-world FinTech scenarios: auto-zoom for small codes, ROI processing, FPS throttling, and exposure compensation for digital screens.

## ✨ Key Features

- **Real-time Scanning** — Powered by CameraX and ML Kit Barcode Scanning
- **Smart Auto-Zoom** — Automatically zooms in on small or distant QR codes using the ZoomSuggestionOptions API
- **ROI (Region of Interest)** — Analyzes only the central 50% of the frame to reduce CPU load and improve detection speed
- **FPS Throttling** — Limits analysis to 20 FPS (50ms interval) to increase sensor exposure time and minimize motion blur
- **Exposure Compensation** — Forced exposure index of -2 for reliable scanning from bright monitors and smartphone screens
- **Native Aspect Ratio** — Uses 4:3 (native sensor ratio) at 1080p to avoid software cropping and maintain pixel density
- **Stability Validation** — Confirms a QR code only after position stabilization (20px tolerance, 300ms delay) to prevent false positives
- **Animated Viewfinder** — A custom 244×244 dp frame with smooth animations that track detected codes

## Screenshots

<!-- Add application screenshots here -->

## 🛠 Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.1.0 |
| Jetpack Compose (BOM) | 2024.12.01 |
| Material 3 | Latest |
| CameraX | 1.4.2 |
| ML Kit Barcode Scanning | 18.3.1 |
| Compose Lifecycle | 2.8.7 |
| AGP | 8.7.3 |

## 📋 Requirements & Permissions

| Parameter | Value |
|-----------|-------|
| Min SDK | 24 (Android 7.0) |
| Target SDK | 35 (Android 15) |
| Java / JVM | 17 |

Hardware: device must have a functional camera (`android.hardware.camera` — required).

### Permissions

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

Note: permissions are requested at runtime using the Activity Result API.

## 🏗 Architecture

The project follows the **MVVM + UDF (Unidirectional Data Flow)** pattern:

```
UI (Composable) → Action → ViewModel → StateFlow<State> → UI
```

### Project Structure

```
com.demo.qrscanner/
├── MainActivity.kt              — Entry point, Compose setup
├── QrScannerScreen.kt           — UI Layer (Composables)
├── QrScannerViewModel.kt        — Business logic & state management
├── QrScannerState.kt            — Immutable UI state model
├── QrScannerAction.kt           — Sealed interface for UI events
├── QrCodeAnalyzer.kt            — CameraX + ML Kit integration pipeline
├── QrFrameProcessor.kt          — Frame processing & detection logic
├── CameraUtils.kt               — Camera utilities (scanner factory, coordinate mapping)
├── AnimatedScanningFrame.kt     — Custom UI for the animated viewfinder
└── QrScannerLayoutDefaults.kt   — Design system constants
```

### Separation of Responsibilities

| Layer | File | Responsibility |
|-------|------|----------------|
| Presentation | `QrScannerScreen.kt`, `AnimatedScanningFrame.kt` | Composable functions, rendering, animations |
| State Management | `QrScannerViewModel.kt` | Action handling, `StateFlow` management |
| State Model | `QrScannerState.kt`, `QrScannerAction.kt` | State model and event definitions |
| Camera | `QrCodeAnalyzer.kt`, `QrFrameProcessor.kt` | CameraX pipeline, frame analysis, ML Kit |
| Utilities | `CameraUtils.kt`, `QrScannerLayoutDefaults.kt` | Helper functions and constants |

## ⚙️ Scanning Optimizations

### Region of Interest (ROI)

Instead of analyzing the full 1080p frame, only the central area (50% width/height) is processed. A 25% padding is added around the ROI to include the QR "quiet zone" (white border), which is critical for ML Kit's detection algorithm.

### Smart Auto-Zoom

When ML Kit detects a QR code but cannot decode it due to low resolution (e.g., the code is too small or far), it triggers `ZoomSuggestionOptions`. The camera then smoothly zooms (300ms duration, `DecelerateInterpolator`) to capture more detail.

- **Base zoom:** 1.3x to avoid "searching" at close range
- **Threshold:** 0.1x change required to prevent lens jitter

### FPS Throttling

Analysis is capped at 20 FPS (50ms interval). At lower capture rate the sensor gets more exposure time per frame, which reduces motion blur when moving the device.

### Exposure Compensation

The exposure index is set to -2. This darkens the image but reveals QR codes on bright displays (monitors, phone screens) that would otherwise be overexposed.

### Stability Validation

Before confirming a scan, QR position stability is checked:

- Frame coordinates must not change by more than 20 px
- Stability must hold for 300 ms

This reduces false positives when the camera is moving.

## 📊 Key Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `DEFAULT_ZOOM_RATIO` | 1.3 | Base zoom level |
| `ZOOM_CHANGE_THRESHOLD` | 0.1 | Minimum zoom change step |
| `ZOOM_ANIMATION_DURATION_MS` | 300 ms | Zoom animation duration |
| `ANALYSIS_INTERVAL_MS` | 50 ms | Frame analysis interval (20 FPS) |
| `ROI_SIZE_RATIO` | 0.5 | ROI size relative to frame |
| `EXPOSURE_COMPENSATION_INDEX` | -2 | Exposure compensation for digital screens |
| `BOUNDS_TOLERANCE` | 20 px | Position stability tolerance |
| `QR_STABILITY_DELAY_MS` | 300 ms | Delay before confirming scan |
| `ANIMATION_DELAY_MS` | 300 ms | Delay before showing result |

## 🚀 Getting Started

```bash
# Clone the repository
git clone https://github.com/simoniddd/qrscanner-demo.git
cd qrscanner-demo

# Build the project
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

Or open the project in Android Studio (Ladybug or newer) and run on a device or emulator.

## User Flow

1. On first launch, the app requests camera permission
2. After granting permission, the scanning screen opens with camera and viewfinder frame
3. Point the camera at a QR code — when detected, the frame animates to the code position
4. If the code is small, the camera automatically zooms in
5. After stable recognition, the result screen is shown
6. The "Scan Again" button returns to scanning

## License

This project is licensed under the [MIT License](LICENSE).
