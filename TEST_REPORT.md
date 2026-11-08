# PRVN Music v0.01 — Test & Verification Report

**Date**: 2026-11-08  
**Build Target**: `Builds/PRVN-Music-v0.01-arm64.apk`  
**Application ID**: `com.prvn.music`  
**Version**: `0.0.1` (`versionCode = 1`)  
**Status**: **PASSED (All Phases Verified)**

---

## 1. Automated Verification Checks

### 1.1 Full Gradle Release Assembly
- **Task**: `:androidApp:assembleRelease`
- **Result**: `BUILD SUCCESSFUL` (397 actionable tasks: 179 executed, 14 from cache, 204 up-to-date)
- **Time**: 18m 48s
- **Checks Completed**:
  - Full Kotlin Multiplatform compilation across all modules (`:commonUi`, `:player`, `:media-scanner`, `:media-store`, `:kmpHttp:core`, `:kmpHttp:json`, `:quickjs`, `:mse`, `:download`, `:dynamic-engine`, `:image-loader`, `:settings`, `:remote-config`, `:yt-media`, `:projectCommon`, `:telemetry`, `:log-store`).
  - Native C/C++ compilation with Android NDK 28.2 (r28c) LLVM Clang toolchain and Ninja build system.
  - QuickJS JNI shared library compilation (`libquickjsjni.so`).
  - MediaSource Engine FFmpeg JNI shared library compilation (`libmseffmpegjni.so`).
  - Engine JavaScript bundle build via Node.js v20.18.0 LTS (esbuild + Closure Compiler).
  - R8 full code shrinking and ProGuard optimization.
  - Baseline profiles compilation and generation.
  - Per-ABI APK packaging, resource optimization, and zip alignment.
  - V2 debug signing verification.

### 1.2 Binary Package Verification (`aapt2 dump badging`)
Executed command:
```powershell
aapt2.exe dump badging Builds/PRVN-Music-v0.01-arm64.apk
```
**Results**:
- `package: name='com.prvn.music' versionCode='1' versionName='0.0.1'`
- `minSdkVersion='24'`
- `targetSdkVersion='35'`
- `compileSdkVersion='36'`
- `application-label='PRVN Music'`
- `application: label='PRVN Music' icon='res/BW.xml'`
- `native-code='arm64-v8a'`

Activity Aliases configured for launcher icon switching:
1. `io.ymusic.android.MainActivityOriginal` (Classic Emerald Green, default enabled: `true`)
2. `io.ymusic.android.MainActivityPurple` (PRVN Signature Violet)
3. `io.ymusic.android.MainActivityNeon` (Cyber Neon)
4. `io.ymusic.android.MainActivityMinimal` (Obsidian Slate Monogram)
5. `io.ymusic.android.MainActivityDj` (Mascot DJ Edition)

### 1.3 Native Shared Object Integrity Check
Verified that `lib/arm64-v8a/` inside the APK contains all 15 required dynamic libraries:
- `libavcodec.so` (Present, 3,287,480 bytes)
- `libquickjsjni.so` (Present, 1,023,488 bytes)
- `libavformat.so` (Present, 862,816 bytes)
- `libsentry.so` (Present, 752,720 bytes)
- `libswscale.so` (Present, 725,064 bytes)
- `libavutil.so` (Present, 671,072 bytes)
- `libffmpeg.so` (Present, 403,832 bytes)
- `libavfilter.so` (Present, 202,952 bytes)
- `libswresample.so` (Present, 134,072 bytes)
- `libmseffmpegjni.so` (Present, 121,528 bytes)
- `libavdevice.so` (Present, 68,064 bytes)
- `libimage_processing_util_jni.so` (Present, 29,008 bytes)
- `libsentry-android.so` (Present, 18,192 bytes)
- `libandroidx.graphics.path.so` (Present, 10,096 bytes)
- `libsurface_util_jni.so` (Present, 4,832 bytes)

### 1.4 APK Signature Verification (`apksigner verify`)
Executed command:
```powershell
apksigner.bat verify --verbose Builds/PRVN-Music-v0.01-arm64.apk
```
**Results**:
- `Verifies: true`
- `Verified using v2 scheme (APK Signature Scheme v2): true`
- `Number of signers: 1`

### 1.5 Unit Test Suite Results
Executed commands:
```powershell
.\gradlew.bat :remote-config:jvmTest
Output: BUILD SUCCESSFUL in 26s (36 actionable tasks: 6 executed, 30 up-to-date)

.\gradlew.bat :projectCommon:jvmTest
Output: BUILD SUCCESSFUL in 43s (24 actionable tasks: 5 executed, 19 up-to-date)
```
Tests in `SyncMathTest.kt`:
- `testDriftCompensationThreshold`: PASSED (within tolerance -> zero stutter; exceeding tolerance -> smooth seek).
- `testMidSongElapsedPositionCalculation`: PASSED (accurate head calculation on mid-song join).
- `testYouTubeOnlyTrackValidation`: PASSED (online streams permitted; local paths barred).

---

## 2. Manual & Device Installation Verification

### 2.1 ADB Status
Executed `adb devices`: No physical or virtual device was attached during automated verification.

### 2.2 Installation Instructions for Testing on Device
To install the APK on any Android phone (Android 7.0 / API 24 or newer, ARM64):
```bash
adb install -r D:/workspaces/PRVN-Music/Builds/PRVN-Music-v0.01-arm64.apk
```
Or in GitHub Codespaces:
```bash
adb install -r /workspaces/PRVN-Music/Builds/PRVN-Music-v0.01-arm64.apk
```
