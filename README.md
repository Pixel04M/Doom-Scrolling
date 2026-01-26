# Scrollable - Hand Gesture Scrolling App

An Android application that allows users to scroll their phone screen remotely using hand gestures detected by the device camera.

## Features

- **Hand Detection**: Uses MediaPipe Hand Landmarker to detect hands in real-time
- **Simple Gestures**: Open palm for scrolling, closed fist for pause
- **System-Wide Scrolling**: Uses Accessibility Services to scroll any app on the device
- **Real-Time Processing**: Processes camera frames continuously for smooth gesture recognition
- **Smart Thresholds**: Ignores accidental small movements to prevent unwanted scrolling
- **Distance-Friendly**: Works from arm's length or more, similar to TikTok/YouTube gesture controls

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Camera**: CameraX
- **Hand Detection**: MediaPipe Hands
- **System Integration**: AccessibilityService

## Prerequisites

- Android Studio (latest stable version)
- Android SDK 24+ (minimum), 36 (target)
- MediaPipe Hand Landmarker model file

## Setup Instructions

### 1. Download MediaPipe Hand Landmarker Model

1. Download the MediaPipe Hand Landmarker model from:
   - [MediaPipe Hand Landmarker](https://developers.google.com/mediapipe/solutions/vision/hand_landmarker#models)
   - Download the `hand_landmarker.task` file

2. Place the model file in the assets folder:
   ```
   app/src/main/assets/hand_landmarker.task
   ```
   
   **Important**: If the `assets` folder doesn't exist, you must create it manually:
   - In Android Studio: Right-click `app/src/main` → New → Folder → Assets Folder
   - Or create the directory: `app/src/main/assets/`
   - Then copy `hand_landmarker.task` into this folder

### 2. Build and Install

1. Open the project in Android Studio
2. Sync Gradle files
3. Build the project
4. Install on a physical Android device (camera access requires a real device)

### 3. Grant Permissions

#### Camera Permission
- The app will request camera permission on first launch
- Grant the permission when prompted

#### Accessibility Permission
1. Open Android Settings
2. Navigate to **Accessibility** → **Installed apps** (or **Downloaded apps**)
3. Find **Scrollable** in the list
4. Enable the service
5. Confirm the permission dialog

## Usage

1. **Launch the app** - The camera preview will appear
2. **Grant permissions** - Ensure both camera and accessibility permissions are granted
3. **Enable Gesture Scrolling** - Tap the "Enable Gesture Scrolling" button
4. **Position your hand** - Show your open palm facing the camera
5. **Scroll**:
   - **Open palm moving DOWN** → Scrolls **DOWN** on screen
   - **Open palm moving UP** → Scrolls **UP** on screen
6. **Pause**: **Close fist and hold steady for 0.5 seconds** → Pauses scrolling
7. **Resume**: **Show open palm and hold steady** → Resumes scrolling
8. **Disable** - Tap "Disable Gesture Scrolling" to stop

## Project Structure

```
app/src/main/
├── java/com/example/myapplication/
│   ├── MainActivity.kt              # Main UI and camera setup
│   ├── HandDetectionAnalyzer.kt    # MediaPipe hand detection
│   ├── GestureScrollController.kt   # Scroll gesture logic
│   └── ScrollAccessibilityService.kt # System-wide scrolling
├── res/
│   ├── xml/
│   │   └── accessibility_service_config.xml
│   └── values/
│       └── strings.xml
└── assets/
    └── hand_landmarker.task         # MediaPipe model (you need to add this)
```

## Key Components

### MainActivity
- Manages camera lifecycle using CameraX
- Handles permissions (camera and accessibility)
- Provides UI for enabling/disabling gesture scrolling
- Integrates hand detection with scroll controller

### HandDetectionAnalyzer
- Processes camera frames using MediaPipe Hand Landmarker
- Detects open palm gesture (4+ fingers raised)
- Detects closed fist gesture (4+ fingers closed)
- Extracts palm center position (wrist) for movement tracking
- Returns gesture state and palm position

### GestureScrollController
- Tracks palm movement over time
- Calculates scroll direction (up/down) based on palm movement
- Detects pause gesture (closed fist held steady for 0.5s)
- Detects resume gesture (open palm held steady)
- Applies thresholds to ignore small movements
- Converts normalized palm movement to scroll pixels

### ScrollAccessibilityService
- Implements AccessibilityService for system-wide access
- Performs scroll gestures on the active window
- Manages service lifecycle and state

## Configuration

### Scroll Sensitivity
Adjust in `GestureScrollController.kt`:
- `scrollThreshold`: Minimum movement to trigger scroll (default: 0.02)
- `scrollCooldown`: Minimum time between scrolls in ms (default: 50)
- `calculateScrollAmount()`: Adjust the multiplier to change scroll speed

### Hand Detection
Adjust in `HandDetectionAnalyzer.kt`:
- `setMinHandDetectionConfidence()`: Detection confidence threshold
- `setMinHandPresenceConfidence()`: Presence confidence threshold
- `setMinTrackingConfidence()`: Tracking confidence threshold

## Important Notes

### MediaPipe API Compatibility
The MediaPipe Tasks Vision API may vary slightly between versions. If you encounter compilation errors related to:

**Common Issues and Fixes:**

1. **`Image.createFromBitmap()` not found:**
   - Try: `com.google.mediapipe.tasks.vision.core.Image(bitmap)`
   - Or: `com.google.mediapipe.tasks.vision.core.Image(bitmap, rotationDegrees)`

2. **`HandLandmarker.createFromOptions()` errors:**
   - Ensure you're passing `context` as the first parameter: `HandLandmarker.createFromOptions(context, options)`
   - Check that `hand_landmarker.task` is in `app/src/main/assets/`

3. **Type mismatches (Int vs Long):**
   - For timestamps, use: `System.currentTimeMillis()` (returns Long)
   - For rotation: `imageProxy.imageInfo.rotationDegrees.toLong()` if needed

4. **Unresolved references:**
   - Sync Gradle: File → Sync Project with Gradle Files
   - Invalidate caches: File → Invalidate Caches / Restart
   - Ensure MediaPipe dependencies are correctly added in `gradle/libs.versions.toml`

**If errors persist:**
- Check the [MediaPipe Tasks Vision documentation](https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker/android)
- Verify your MediaPipe version matches the API usage
- Consider using MediaPipe version 0.10.9 or check the latest stable version

## Troubleshooting

### Camera not working
- Ensure you're using a physical device (emulators don't support camera)
- Check that camera permission is granted
- Verify the device has a rear camera

### Hand detection not working
- Ensure `hand_landmarker.task` is in `app/src/main/assets/`
- Check that lighting is adequate
- Ensure hands are clearly visible in the camera view
- Try adjusting detection confidence thresholds

### Scrolling not working
- Verify Accessibility Service is enabled in Settings
- Check that "Enable Gesture Scrolling" is toggled on
- Ensure two fingers are clearly raised and visible
- Try moving fingers more deliberately

### App crashes
- Check Logcat for error messages
- Ensure all dependencies are synced in Gradle
- Verify MediaPipe model file is correctly placed

## Limitations

- Requires physical Android device (camera access)
- Works best with good lighting conditions
- Requires both camera and accessibility permissions
- May have reduced accuracy in low light or with fast movements

## License

This project is provided as-is for educational purposes.

## Acknowledgments

- Uses [MediaPipe](https://mediapipe.dev/) for hand detection
- Uses [CameraX](https://developer.android.com/training/camerax) for camera access
- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose)
