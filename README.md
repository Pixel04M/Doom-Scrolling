

<h1 align="center">Doom Scrolling </h1>
<h3 align="center">Scroll your Android phone with simple hand gestures — no touching required</h3>

<p align="center">
  Control any app hands-free using your phone's front camera • Like TikTok/YouTube air gestures but system-wide
</p>

<p align="center">
  <a href="https://github.com/Pixel04M/Doom-Scrolling/releases/latest">
    <img src="https://img.shields.io/badge/Download%20APK-brightgreen?style=for-the-badge&logo=android&logoColor=white" alt="Download APK">
  </a>
  <a href="https://github.com/Pixel04M/Doom-Scrolling/stargazers">
    <img src="https://img.shields.io/github/stars/Pixel04M/Doom-Scrolling?style=for-the-badge&color=yellow" alt="GitHub stars">
  </a>
  <img src="https://img.shields.io/github/license/Pixel04M/Doom-Scrolling?style=for-the-badge" alt="License">
</p>

---

### ✨ Features

- Real-time **hand detection** with MediaPipe Hand Landmarker  
- Super simple gestures:  
   **Open palm** → scroll up/down  
   **Closed fist** → pause scrolling  
- **System-wide scrolling** — works in any app (thanks to Accessibility Service)  
- Smooth & responsive — continuous camera frame processing  
- Smart movement filtering — ignores tiny accidental twitches  
- Comfortable range — works great from arm's length or farther  



---

### 🛠️ Tech Stack

| Technology          | Purpose                          | Badge                                                                 |
|---------------------|----------------------------------|-----------------------------------------------------------------------|
| **Kotlin**          | Main language                    | ![](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white) |
| **Jetpack Compose** | Modern UI toolkit                | ![](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white) |
| **CameraX**         | Camera handling                  | ![](https://img.shields.io/badge/CameraX-689F38?style=flat&logo=android&logoColor=white) |
| **MediaPipe Hands** | Hand landmark detection          | ![](https://img.shields.io/badge/MediaPipe-Hands-success?style=flat) |
| **AccessibilityService** | Simulate scrolling anywhere | ![](https://img.shields.io/badge/Accessibility-Service-blue?style=flat) |

---

###  Getting Started

#### Prerequisites

- Android Studio **Koala** | **Ladybug** (or newer)
- minSdk 24+ • targetSdk 36 (Android 16)
- MediaPipe Hand Landmarker **task bundle** (`.task` file)

#### Steps

1. Clone the repo  
   ```bash
   git clone https://github.com/Pixel04M/Doom-Scrolling.git
