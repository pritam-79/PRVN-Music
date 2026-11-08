# PRVN Music — Complete Development Setup Guide

This guide walks you through setting up the complete **PRVN Music** development pipeline across all six supported Windows tools.

---

## 1. Toolchain Overview

The PRVN Music development pipeline integrates:
1. **Google Antigravity**: AI coding agent and orchestration environment.
2. **Android Studio**: Official Android IDE and AVD manager (`C:\Program Files\Android\Android Studio`).
3. **Eclipse Temurin JDK 21**: High-performance Java 21 LTS runtime (`C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`).
4. **Node.js 22 LTS**: JavaScript engine bundler (`C:\Program Files\nodejs`).
5. **Git for Windows & GitHub CLI**: Version control and release publishing (`C:\Program Files\Git`).
6. **MSYS2 UCRT64 & FFmpeg**: Native C/C++ compiler and audio processing (`C:\msys64`).

---

## 2. Environment Variables Configuration

The following User environment variables are permanently configured:

| Variable | Recommended Value |
| :--- | :--- |
| `JAVA_HOME` | `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` |
| `ANDROID_HOME` | `C:\Users\prita\.tools\android-sdk` |
| `ANDROID_SDK_ROOT` | `C:\Users\prita\.tools\android-sdk` |
| `Path` (append) | `%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\cmdline-tools\latest\bin` |

---

## 3. Working with Android Studio

1. Open Android Studio.
2. Select **Open** and choose `D:\workspaces\PRVN-Music`.
3. When prompted for Gradle JDK, select **Eclipse Temurin JDK 21** (`C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`).
4. Android Studio will automatically recognize the unified SDK at `C:\Users\prita\AppData\Local\Android\Sdk`.

---

## 4. Running the App on an Android Device or Emulator

### Option A: Physical Android Device (Recommended)
1. Enable **Developer Options** on your Android phone (Tap *Build Number* 7 times in Android Settings).
2. Enable **USB Debugging**.
3. Connect your phone via USB cable and allow USB debugging when prompted.
4. Verify connection:
   ```powershell
   adb devices
   ```
5. Install and launch the v0.03 Release APK:
   ```powershell
   adb install -r "D:\workspaces\PRVN-Music\PRVN-MUSIC-RELEASE\Android\PRVN-Music-Android.apk"
   adb shell monkey -p com.prvn.music -c android.intent.category.LAUNCHER 1
   ```

### Option B: Android Emulator (AVD)
1. The virtual device `Medium_Phone_API_37.0` is already configured in `C:\Users\prita\.android\avd\Medium_Phone.avd`.
2. To enable hardware acceleration on Windows, run the one-time AEHD hypervisor installer as Administrator:
   - Navigate to: `C:\Users\prita\AppData\Local\Android\Sdk\extras\google\Android_Emulator_Hypervisor_Driver`
   - Right-click `silent_install.bat` -> **Run as Administrator**.
3. Start the emulator:
   ```powershell
   C:\Users\prita\AppData\Local\Android\Sdk\emulator\emulator.exe -avd Medium_Phone_API_37.0 -gpu auto
   ```

---

## 5. Compiling and Packaging Releases

### Build Debug APK:
```powershell
.\gradlew.bat :androidApp:assembleDebug
```

### Build Production Release APK:
```powershell
.\gradlew.bat :androidApp:assembleRelease
```

### Build Production Release AAB (Google Play Bundle):
```powershell
.\gradlew.bat :androidApp:bundleRelease
```

Output locations:
- APK: `androidApp/build/outputs/apk/release/PRVN-Music-0.0.2-arm64-v8a-release.apk`
- AAB: `androidApp/build/outputs/bundle/release/PRVN-Music-0.0.2-release.aab`
- Staged Production Directory: `PRVN-MUSIC-RELEASE/Android/`
