# PRVN Music — Build Guide

This document describes how to set up the development environment and compile **PRVN Music** from source code.

---

## 1. Prerequisites

### Required Tools
- **Java Development Kit (JDK)**: OpenJDK 21 LTS (or JDK 17+)
- **Android SDK**:
  - `compileSdk`: 36 (Android 16 Developer Preview)
  - `targetSdk`: 35 (Android 15)
  - `minSdk`: 24 (Android 7.0)
  - `build-tools`: 36.0.0 or 35.0.0
- **Android NDK**: Version `28.2.13676358` (NDK r28c)
- **CMake**: Version 3.22.1 or newer
- **Ninja**: Build system for native CMake targets
- **Node.js**: Node.js LTS v20.x or v22.x (required by `:engine-js:bundleRawJs`)
- **Git**: Version 2.40+

---

## 2. Environment Variables Setup

Ensure the following environment variables are exported before compiling:

### Windows (PowerShell)
```powershell
$env:JAVA_HOME = "C:\Users\<user>\.tools\jdk21\jdk-21.0.6+7"
$env:ANDROID_HOME = "C:\Users\<user>\.tools\android-sdk"
$env:ANDROID_NDK_ROOT = "$env:ANDROID_HOME\ndk\28.2.13676358"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\build-tools\36.0.0;$env:PATH"
```

### Linux / macOS (Bash)
```bash
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/28.2.13676358"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

---

## 3. Native FFmpeg ARM64 Libraries

PRVN Music relies on native ARM64 FFmpeg libraries for audio decoding, stream parsing, and metadata extraction. The libraries are staged from the sibling `ffmpeg-build` directory (`../ffmpeg-build/out/android-arm64-v8a`):

Required DSOs:
- `libavcodec.so`
- `libavformat.so`
- `libavutil.so`
- `libavfilter.so`
- `libswresample.so`
- `libswscale.so`
- `libffmpeg.so`

---

## 4. Compiling & Assembling

### Run Unit Tests
```bash
./gradlew :projectCommon:jvmTest
```

### Compile Kotlin & Compose Multiplatform
```bash
./gradlew :commonUi:compileAndroidMain :androidApp:compileReleaseKotlin
```

### Assemble Full Release APK
```bash
./gradlew :androidApp:assembleRelease
```

The compiled release APK is generated at:
```
androidApp/build/outputs/apk/release/PRVN-Music-0.0.2-arm64-v8a-release.apk
```

---

## 5. Binary Verification

Verify the generated package with Android SDK build-tools:

```bash
# Package metadata, permissions, and launchable activities
aapt2 dump badging androidApp/build/outputs/apk/release/PRVN-Music-0.0.2-arm64-v8a-release.apk

# APK Signature verification
apksigner verify --verbose androidApp/build/outputs/apk/release/PRVN-Music-0.0.2-arm64-v8a-release.apk
```
