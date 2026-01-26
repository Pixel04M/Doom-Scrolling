# Scrollable - Hand Gesture Scrolling App

## Overview
This is a **native Android application** written in Kotlin with Jetpack Compose that enables hands-free scrolling through hand gesture recognition using the device camera and MediaPipe.

## Project Type
- **Platform**: Android (Native)
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Build System**: Gradle with Kotlin DSL
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36

## Key Features
- Real-time hand detection using MediaPipe
- Two-finger gesture recognition for scrolling
- System-wide scrolling via Accessibility Services
- CameraX integration for camera access

## Project Structure
```
app/src/main/
├── java/com/example/myapplication/
│   ├── MainActivity.kt              # Main UI and camera setup
│   ├── HandDetectionAnalyzer.kt     # MediaPipe hand detection
│   ├── GestureScrollController.kt   # Scroll gesture logic
│   └── ScrollAccessibilityService.kt # System-wide scrolling
├── res/                             # Android resources
└── assets/                          # MediaPipe model (needs to be added)
```

## Important: Replit Environment Limitations
This is a native Android application that **cannot run directly in the Replit environment** because:

1. **No Android SDK**: Android apps require the Android SDK/NDK which is not available in Replit
2. **No Emulator**: Android emulators require x86 virtualization not supported in this environment
3. **Physical Device Required**: The app uses camera features that require a real Android device

## How to Build/Run This Project

### For Development (Recommended):
1. Clone/download this repository
2. Open in **Android Studio**
3. Sync Gradle files
4. Download MediaPipe hand_landmarker.task model
5. Place model in `app/src/main/assets/`
6. Build and install on a physical Android device

### Prerequisites:
- Android Studio (latest stable)
- Android SDK 24+
- Physical Android device (camera required)
- MediaPipe hand_landmarker.task model file

## Configuration Notes
- Scroll sensitivity adjustable in `GestureScrollController.kt`
- Detection thresholds adjustable in `HandDetectionAnalyzer.kt`

## Recent Changes
- Initial import to Replit (January 2026)
