# PRVN Music

PRVN Music is a high-performance, Kotlin Multiplatform music streaming and local audio player application for Android, featuring real-time synchronized group playback (Play Together) and a dark interface.

Based on the open-source YMusic V4 project under GNU General Public License v3.0.

---

## Features

- **Online Music Streaming & Search**: YouTube Music catalog integration via QuickJS and MediaSourceEngine (MSE).
- **Background Playback**: Continuous playback with Android media style notification controls and lock-screen support.
- **Play Together (Group Listening)**: Real-time room synchronization with custom 4-digit codes, private/invite/public room visibility, and live playback synchronization.
- **Audio Equalizer & Sound Effects**: Graphic equalizer, virtualizer, bass boost, and audio speed/pitch adjustment.
- **Offline Download Manager**: Progressive and adaptive audio stream extraction (MP3 / M4A) via integrated FFmpeg.
- **Multiple App Icons**: Customizable launcher icons (Original, PRVN Purple, PRVN Neon, Minimal, and DJ).
- **Local Audio Library**: Automatic on-device MediaStore scanning and metadata parsing via `libffmpeg.so`.

---

## Building

### Prerequisites
- **JDK 17+** (Recommended: OpenJDK 21 LTS)
- **Android SDK** (API 36, Build-Tools 35/36)
- **Android NDK** (r28c / `28.2.13676358`)
- **CMake 3.20+** with **Ninja**
- **Node.js** (LTS v20+ for engine JavaScript bundling)

### FFmpeg Integration
Prebuilt ARM64 native FFmpeg libraries (`libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`, `libswscale.so`, `libavfilter.so`, `libffmpeg.so`) are staged from the sibling `ffmpeg-build` repository (`../ffmpeg-build/out/android-arm64-v8a`).

### Android APK Build
```bash
./gradlew :androidApp:assembleRelease
```
The resulting APK is generated at:
`Builds/PRVN-Music-v0.02-arm64.apk`

---

## License & Attribution

- **License**: GNU General Public License v3.0 (GPL-3.0)
- **Base Upstream Project**: [YMusic V4](https://github.com/ymusic-io/YMusicV4)
- **Third-Party Libraries**: FFmpeg (LGPL-2.1+), QuickJS (MIT), Jetpack Compose (Apache-2.0), AndroidX (Apache-2.0). See [LICENSES_AND_ATTRIBUTION.md](LICENSES_AND_ATTRIBUTION.md) for full notices.
