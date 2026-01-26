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
[![Download Android APK](https://img.shields.io/badge/Download-APK-brightgreen?style=for-the-badge&logo=android&logoColor=white)](app/release/Elder%20People%20Fall%20Detection%20.apk)

