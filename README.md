# PRVN Music

<div align="center">
  <img src="assets/branding/prvn_music_logo.png" width="140" height="140" alt="PRVN Music Logo (Minimal Neon)" />
  <h2>PRVN Music v0.16.1</h2>
  <p><strong>A Next-Generation Music Experience — High-Fidelity Streaming, Dynamic Player Styles, Synchronized Lyrics, Community Group Listening & Native In-App Updates</strong></p>

  <p>
    <a href="https://github.com/pritam-79/PRVN-Music/releases/tag/v0.16.1"><img src="https://img.shields.io/badge/Release-v0.16.1-A855F7?style=for-the-badge&logo=android&logoColor=white" alt="Release v0.16.1" /></a>
    <a href="https://github.com/pritam-79/PRVN-Music/releases/tag/v0.16.1"><img src="https://img.shields.io/badge/Build-21-06B6D4?style=for-the-badge" alt="Build 21" /></a>
    <a href="https://github.com/pritam-79/PRVN-Music/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge" alt="License GPL-3.0" /></a>
    <a href="https://github.com/pritam-79/PRVN-Music"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20Desktop-purple?style=for-the-badge" alt="Multiplatform" /></a>
  </p>
</div>

---

## What's New in v0.16.1

PRVN Music v0.16.1 brings critical playback synchronization fixes to Group Listening (Listen Together), refreshed About Us branding, and refined release metadata:

- 👥 **Listen Together Playback Synchronization Repair:** Fixed a critical glitch where listener devices automatically paused when the host changed songs. Listeners now seamlessly transition and continue playing in real time along with the host.
- ⏱️ **Host-Aligned Position Resync:** Fixed the issue where listeners resuming or playing started from `0:00` instead of aligning with the host's actual server-calibrated playhead.
- ⚡ **Enhanced Buffer Barrier & Network Compensation:** Polling barrier ensures guest audio extraction completes before playback resumes without stalling the room.
- 🎨 **Official "About Us -> Version" Branding:** Completely replaced legacy SimpMusic branding inside Settings -> About Us -> Version (`CreditScreen`) with the official PRVN Music Minimal Neon identity, updated attribution to PRVN (pritam-79), and refreshed legal terms of use.
- 🚀 **Updated Documentation & Release Metadata:** Comprehensive feature guide for group listening rooms and updated version codes across all build variants.

- 📱 **Real Android Launcher Icon Switching:** Fixed dynamic launcher icon switching across all Android devices (Samsung OneUI, Xiaomi MIUI/HyperOS, OnePlus OxygenOS, Google Pixel, Realme, OPPO, Vivo, Motorola, Nothing). Uses native Android `Activity-Alias` components managed via `PackageManager` with safe-state transitions, preventing launcher icon removal or app crashes.
- 🎯 **18 Official Reference Logos Preserved:** Complete preservation of the 18 official logos from Concept Sheets 1 and 2 without distortion, stretching, or cropping. Includes the official default **V1 — Minimal Neon** and dual special editions (**V6 & V10 — Liquid Glass**).
- 🛡️ **Android Adaptive Icon Safe-Zone Compliance:** All adaptive launcher icons are engineered to maintain complete visibility within the 66dp Android safe zone. Lettering (`PRVN MUSIC`) and glowing symbols remain unclipped under circular, squircle, pebble, and rounded-square OEM launcher masks.
- 🔄 **Multi-Density Resource Pipeline:** Full density mipmaps generated for `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`, and API 26+ `mipmap-anydpi-v26` adaptive layers.
- 🧹 **Complete Project Brand Cleansing:** Eradicated all inherited legacy YMusic icon files and XML drawables, aligning repository branding with the official PRVN Music identity.
- 🚀 **Universal Native In-App Updater:** Direct HTTPS background downloads, real-time byte counters (`XX.X MB / YY.Y MB`), transfer speed meter, ETA, pause/resume, cancel/retry, and SHA-256 APK integrity verification.

---

## Core Feature Highlights

### 1. Three Immersive Player Styles
- **Classic Player:** Minimalist, clean controls with smooth gestures and waveform seekbar.
- **Material 3 Expressive Player:** Google's expressive fluid motion system with responsive color extraction.
- **Apple Music-Inspired Player:** Elegant frosted liquid-glass backdrop, floating capsule controls, and synchronized lyrics overlay.

### 2. Audio Engineering & DSP
- **10-Band Graphic Equalizer:** Precision frequency manipulation with custom user presets.
- **AutoEq Headphone Profiles:** Integrated correction profiles for hundreds of audiophile headphone models.
- **AutoMix & Gapless Playback:** Seamless transitions with BPM matching and key detection.
- **Volume Normalization (ReplayGain):** Consistent loudness across diverse music catalogs.

### 3. Advanced Synced Lyrics
- Word-by-word synchronized karaoke lyrics.
- Real-time lyrics romanization (Japanese Furigana/Romaji, Korean Romaja, Chinese Pinyin, Cyrillic).
- Dual-engine lyrics translation and user timing offsets (±100ms adjustments).
- Shareable lyrics quote cards.

### 4. Group Listening (Listen Together)
- **Real-Time Synchronized Playback:** Join or host listening rooms using intuitive 4-digit room codes or custom WebSocket servers (`wss://`).
- **Low-Latency Sub-500ms Synchronization:** Real-time drift correction, monotonic clock calibration, and network latency compensation across all connected devices.
- **Collaborative Queue Management:** Listeners can suggest songs and upvote queued tracks, letting the room collectively decide upcoming music.
- **Full Host Governance:** Automatic join approvals or manual approval prompts, member kicking, host transfer, and persistent user blocklisting.
- **Cross-Platform Support:** Effortlessly synchronize music across Android phones, tablets, and Desktop computers.

---

## Downloads & Distribution

| Package | Target Platform | Architecture | Status |
| :--- | :--- | :--- | :--- |
| `PRVN-Music-v0.16.1-arm64.apk` | Android 8.0+ (API 26+) | `arm64-v8a` | **Recommended** |
| `PRVN-Music-v0.16.1-universal.apk` | Android 8.0+ (API 26+) | Universal (All ABIs) | Available |
| Desktop JAR / Native | Windows / Linux / macOS | x86_64 / aarch64 | JVM Target Available |

### Installation & Launcher Icon Notes
- **Launcher Caching:** When switching the app icon in **Settings → User Interface → App Icon**, Android updates the system `PackageManager` immediately. On certain OEM launchers (such as Samsung OneUI or Xiaomi HyperOS), the launcher may take a few moments to refresh its disk cache.
- **Upgrade Continuity:** Upgrading from v0.14 or v0.15 preserves all existing playlists, downloads, favorite tracks, and user settings seamlessly.

---

## Architecture & Open Source Attribution
PRVN Music is built using **Kotlin Multiplatform** and **Compose Multiplatform**. It builds upon the open-source foundation of [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by Tuan Minh Nguyen Duc (maxrave-dev) licensed under **GNU General Public License v3.0 (GPL-3.0)**.
