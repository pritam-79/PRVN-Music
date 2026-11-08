# PRVN Music v0.01 — Dependencies

This document provides the complete list of build tools, SDKs, native compilers, runtimes, and libraries required to build PRVN Music v0.01.

---

## 1. Toolchains & Environment

| Component | Version | Location / Source | Purpose |
| :--- | :--- | :--- | :--- |
| **JDK** | OpenJDK 21.0.6 LTS (Temurin) | `C:\Users\prita\.tools\jdk21` | Kotlin & Gradle JVM runtime |
| **Android SDK** | API 36 (Vanilla Ice Cream) | `C:\Users\prita\.tools\android-sdk` | Android framework platform |
| **Android Build-Tools** | 36.0.0 & 35.0.0 | `android-sdk/build-tools` | `aapt2`, `d8`, `zipalign`, `apksigner` |
| **Android NDK** | 28.2.13676358 (r28c) | `android-sdk/ndk/28.2.13676358` | Clang/LLVM C11 cross-compiler for ARM64 |
| **CMake** | 4.5.0 | `C:\Program Files\CMake\bin` | Native build configuration generator |
| **Ninja** | 1.12.1 | WinGet Ninja-build package | Native compilation execution engine |
| **Node.js** | v20.18.0 LTS | `C:\Users\prita\.tools\node` | esbuild & Google Closure Compiler runner |
| **Gradle** | 9.4.1 | Gradle Wrapper (`gradlew.bat`) | Multi-project build orchestration |

---

## 2. Core Frameworks & Plugins

- **Android Gradle Plugin (AGP)**: `8.9.1`
- **Kotlin Multiplatform (KMP)**: `2.2.0`
- **Compose Multiplatform**: `1.8.0-alpha03`
- **Kotlin Serialization**: `2.2.0`
- **Sentry Android Gradle Plugin**: `6.18.0`

---

## 3. Native Libraries & Multimedia Engine

- **FFmpeg Engine**: `7.1` (LGPL/GPL v3 build from `ymusic-io/ffmpeg-build`)
  - `libavcodec` — Audio/video decoders & encoders
  - `libavformat` — Container format demuxers/muxers
  - `libavutil` — Core math, memory, and utility functions
  - `libavfilter` — Audio effect and DSP filter graphs
  - `libswresample` — Audio resampling and channel mixing
  - `libswscale` — Video scaling and color matrix conversion
  - `libffmpeg.so` — Executable multimedia scanner & CLI utility
- **QuickJS Engine**: Bellard QuickJS (VERSION 2026-06-04, commit `04be2460`)
  - Lightweight embedded JavaScript runtime executing YouTube cipher / streaming decoders
- **MSE (MediaSource Engine) Bridge**:
  - High-performance native audio/video frame processor connecting ExoPlayer/Media3 with FFmpeg

---

## 4. Key AndroidX & Multiplatform Libraries

- **AndroidX Core KTX**: `1.15.0`
- **AndroidX Activity Compose**: `1.10.1`
- **AndroidX Media / Media3 Session**: `1.5.1` / `1.7.0`
- **AndroidX Biometric**: `1.2.0-alpha05`
- **AndroidX Palette**: `1.0.0`
- **AndroidX Camera (Core, Camera2, Lifecycle, View)**: `1.4.1`
- **Kotlinx Coroutines**: `1.10.1`
- **Zxing Core**: `3.5.3` (QR code sharing & importing)
- **OkHttp / KmpHttp**: Pure KMP networking core
- **Desugar JDK Libs**: Java 8+ API desugaring on Android API < 26
