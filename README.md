# PRVN Music

<div align="center">
  <img src="assets/icons/prvn_purple_icon.png" width="128" height="128" alt="PRVN Music Logo" />
  <h3>Premium Dark Audio Streaming & Synchronized Music Player</h3>
  <p>High-performance Kotlin Multiplatform music player with real-time group playback (Play Together), background streaming, offline downloads, and a dark neon interface.</p>

  <p>
    <a href="https://github.com/pritam-79/PRVN-Music/releases/tag/v0.03"><img src="https://img.shields.io/badge/Release-v0.03-purple.svg?style=flat-square" alt="Version 0.03" /></a>
    <a href="https://github.com/pritam-79/PRVN-Music/releases/tag/v0.03"><img src="https://img.shields.io/badge/Platform-Android_ARM64-cyan.svg?style=flat-square" alt="Platform Android ARM64" /></a>
    <a href="docs/LICENSES.md"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License GPL-3.0" /></a>
  </p>
</div>

---

## Downloads & Platform Support

| Platform | Target Architecture | Status | Release Package |
|---|---|---|---|
| **Android** | `arm64-v8a` (API 24+) | **Available (Verified)** | [Download APK (v0.03)](https://github.com/pritam-79/PRVN-Music/releases/download/v0.03/PRVN-Music-v0.03-arm64.apk) |
| **Windows** | x86_64 (Windows 10/11) | *Pending host C++ toolchain* | [Release Page](https://github.com/pritam-79/PRVN-Music/releases) |
| **macOS** | Apple Silicon / x86_64 | *Pending macOS build environment* | [Release Page](https://github.com/pritam-79/PRVN-Music/releases) |
| **Linux** | x86_64 (GLIBC 2.31+) | *Planned* | [Release Page](https://github.com/pritam-79/PRVN-Music/releases) |
| **iOS** | iOS 16+ | *Planned (requires Apple signing)* | [Release Page](https://github.com/pritam-79/PRVN-Music/releases) |

> [!NOTE]
> Only genuine, verified binaries are published. Platforms requiring separate OS environments (macOS/iOS) or host compilers (Windows MSVC/MinGW) are not distributed until verified on native toolchains.

---

## Verified Release Specifications (Android)

- **Application ID**: `com.prvn.music`
- **Release Version**: `0.0.3` (Version Code `3`)
- **Package File**: `PRVN-Music-v0.03-arm64.apk`
- **File Size**: `9,817,744` bytes (~9.36 MB)
- **SHA-256 Checksum**:
  ```
  8FFF71A4206DC92542299971F9B04601716F743F1E5F6E3CD32874E5E2F6A390
  ```
- **Signing Scheme**: Android APK Signature Scheme v2 (Verified)
- **Minimum OS Version**: Android 7.0 (API 24)
- **Target OS Version**: Android 15 (API 35, compiled with SDK 36)

---

## Features

### 1. Online Streaming & Catalog Search
- High-efficiency catalog search and streaming powered by embedded QuickJS and MediaSourceEngine (MSE).
- Selectable streaming audio bitrates:
  - **High**: 320 kbps
  - **Medium**: 160 kbps
  - **Low (Data Saver)**: 96 kbps

### 2. Play Together (Synchronized Group Listening)
- **Dedicated Tab**: One-click group room creation and discovery in the main bottom navigation bar.
- **Custom 4-Digit Codes**: Join sessions with memorable 4-digit PINs (e.g. `4821`).
- **Low-Drift Sync Engine**: Real-time position tracking with drift compensation under 500 ms and mid-track catch-up.
- **Dual Backend Support**:
  - *Live Supabase Realtime*: Phoenix Channels WebSocket client configurable in **Settings → Play Together**.
  - *Local Relay Mode*: Automatic fallback to on-device simulated relay when offline or unconfigured.

### 3. Background Playback & Android MediaStyle Controls
- Uninterrupted audio playback in the background via foreground media service.
- Full lock-screen controls, seekbar tracking, and MediaStyle notification integration.

### 4. Dynamic Launcher Icons
Customize your home screen with 5 adaptive launcher themes via **Settings → Appearance**:
- **PRVN Original** (Emerald Green)
- **PRVN Purple** (Signature Dark Violet)
- **PRVN Neon** (Electric Cyan)
- **PRVN Minimal** (Monochrome Dark)
- **PRVN DJ** (Soundwave Edition)

### 5. Sound Effects & Offline Downloads
- 5-band graphic equalizer, virtualizer, bass boost, pitch shifting, and playback speed control.
- Progressive MP3 / M4A offline download manager with pause/resume support.

---

## Quick Installation

### Standard Android Install
1. Download [`PRVN-Music-v0.03-arm64.apk`](https://github.com/pritam-79/PRVN-Music/releases/download/v0.03/PRVN-Music-v0.03-arm64.apk).
2. Open the file in your device's Downloads folder and confirm installation.
3. If prompted, toggle **Allow from this source** in Android Settings.

### Developer Install (ADB)
```bash
adb install -r Builds/PRVN-Music-v0.03-arm64.apk
```

---

## Documentation

Detailed technical and developer documentation is organized in [`docs/`](docs/):

- [Installation Guide](docs/INSTALLATION.md) — Comprehensive install steps and checksum verification.
- [Build Instructions](docs/BUILDING.md) — Toolchain setup, NDK dependencies, and Gradle tasks.
- [Feature Breakdown](docs/FEATURES.md) — In-depth overview of all audio and UI capabilities.
- [Changelog](docs/CHANGELOG.md) — Release notes and version history.
- [Licenses & Attribution](docs/LICENSES.md) — Third-party notices and open-source licenses.

---

## Legal & Open Source Compliance

- **License**: GNU General Public License v3.0 ([GPL-3.0](LICENSE))
- **Base Upstream Foundation**: [YMusic V4](https://github.com/ymusic-io/YMusicV4) by YMusic contributors.
- **Third-Party Libraries**: FFmpeg (LGPL-2.1+ / GPL-2.0+), QuickJS (MIT), Jetpack Compose (Apache-2.0), AndroidX (Apache-2.0).
- Detailed legal notices are available in [`docs/LICENSES.md`](docs/LICENSES.md) and inside the application under **Settings → About PRVN Music → Third-Party Components**.
