# PRVN Music — Installation Guide

This guide covers installing **PRVN Music** on supported mobile devices and operating environments.

---

## Supported Platforms Overview

| Platform | Current Release Status | Supported Architecture / Version | Installation Format |
|---|---|---|---|
| **Android** | **Available (v0.02)** | ARM64 (`arm64-v8a`), Android 7.0+ (API 24+) | `.apk` |
| **Windows** | Pending Host Toolchain | x86_64, Windows 10/11 | `.exe` / `.msi` |
| **macOS** | Pending Apple Host Environment | ARM64 (Apple Silicon) / x86_64, macOS 12+ | `.dmg` |
| **Linux** | Planned | x86_64 | `.AppImage` / `.deb` |
| **iOS** | Planned | iOS 16+ (requires Apple Developer signing) | `.ipa` |

---

## 1. Android Installation (Standard Method)

### Requirements
- Smartphone or tablet running **Android 7.0 (Nougat / API 24)** or newer.
- ARM64 architecture (**arm64-v8a**), the standard for all modern Android smartphones.
- At least 50 MB of free storage space.

### Step-by-Step Instructions
1. Download the verified release APK from the official GitHub Release:  
   **[PRVN-Music-v0.02-arm64.apk](https://github.com/pritam-79/PRVN-Music/releases/download/v0.02/PRVN-Music-v0.02-arm64.apk)**
2. Once the download completes, tap the download notification or locate the `.apk` file in your **Files** or **Downloads** app.
3. If prompted by Android about installing from unknown sources:
   - Tap **Settings** on the prompt.
   - Toggle **Allow from this source** to enabled.
   - Tap **Back** and proceed with the installation.
4. Tap **Install**.
5. Once installation finishes, tap **Open** to launch **PRVN Music**.

---

## 2. Android Installation via ADB (Developer Method)

If your device is connected to your development PC via USB with USB Debugging enabled:

```bash
# Verify device connection
adb devices

# Install or upgrade PRVN Music
adb install -r Builds/PRVN-Music-v0.02-arm64.apk
```

---

## 3. Package Verification & SHA-256 Checksum

Before installing, you can verify the cryptographic integrity of the APK binary:

- **File Name**: `PRVN-Music-v0.02-arm64.apk`
- **File Size**: `9,790,544` bytes (~9.34 MB)
- **SHA-256**: `0A74F52EEA369BCFF5A0A0637BAF4A62AA55C71603217509EADACD62299301FF`
- **Application ID**: `com.prvn.music`
- **Version Code**: `2`
- **Signature**: Android APK Signature Scheme v2 (Verified)

### Verification Commands

**PowerShell (Windows)**:
```powershell
Get-FileHash -Path Builds/PRVN-Music-v0.02-arm64.apk -Algorithm SHA256
```

**Linux / macOS**:
```bash
sha256sum Builds/PRVN-Music-v0.02-arm64.apk
```

---

## 4. Other Platforms (Roadmap & Status)

- **Windows, macOS, Linux, iOS**: Release builds for desktop and iOS targets require specialized host toolchains (MSVC/MinGW for Windows native QuickJS host, macOS + Xcode for DMG/IPA). Genuine builds will be published to the GitHub Releases page as toolchain environments are provisioned.
