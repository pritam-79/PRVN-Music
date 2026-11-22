# PRVN Music

<div align="center">
  <img src="assets/branding/logos/logo_01_512.png" width="140" height="140" alt="PRVN Music Logo (Minimal Neon)" />
  <h2>PRVN Music v0.15</h2>
  <p><strong>A Next-Generation Music Experience — High-Fidelity Streaming, Dynamic Player Styles, Synchronized Lyrics, Community Group Listening & Universal In-App Updates</strong></p>

  <p>
    <a href="https://github.com/pritam-79/PRVN-Music/releases/tag/v0.15.0"><img src="https://img.shields.io/badge/Release-v0.15.0-A855F7?style=for-the-badge&logo=android&logoColor=white" alt="Release v0.15.0" /></a>
    <a href="https://github.com/pritam-79/PRVN-Music/releases/tag/v0.15.0"><img src="https://img.shields.io/badge/Build-19-06B6D4?style=for-the-badge" alt="Build 19" /></a>
    <a href="https://github.com/pritam-79/PRVN-Music/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge" alt="License GPL-3.0" /></a>
    <a href="https://github.com/pritam-79/PRVN-Music"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20Desktop-purple?style=for-the-badge" alt="Multiplatform" /></a>
  </p>
</div>

---

## What's New in v0.15

PRVN Music v0.15 brings a complete architectural release upgrade:
- 🚀 **Universal Native In-App Updater:** Check, download, verify, and initiate installation directly within PRVN Music. No external browser redirects. Features real-time byte counters, speed meter, ETA, pause/resume, and SHA-256 integrity verification.
- 🎨 **17 Official Reference Logos & Dynamic Launcher Switching:** Choose from 17 extracted reference logos (including default **Minimal Neon** and special **Liquid Glass**) directly inside **Settings → User Interface → App Icon**.
- 🛡️ **Google Play Protect & Security Hardening:** Multi-scheme APK signature support (v1, v2, v3, v4) and production keystore pipeline eliminating untrusted debug warnings.
- ⚡ **Zero Tidal HTTP 429 Errors:** High-performance LRU cache, concurrent request deduplication, and circuit-breaker backoff for track audio transition metadata.
- 🎵 **Authentic Listening Receipts:** Generate printable, shareable listening receipts with custom privacy controls (show/hide listener name and listening duration) and zero upstream branding leaks.
- 👥 **Listen Together Community Voting:** Synchronized group listening with room queue upvoting, song requests, and host controls.

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
- Join or host real-time listening rooms using intuitive 4-digit codes.
- Low-latency sub-500ms sync across devices.
- Queue upvoting: listeners vote on upcoming tracks.

---

## Downloads & Distribution

| Package | Target Platform | Architecture | Status |
| :--- | :--- | :--- | :--- |
| `PRVN-Music-v0.15-arm64.apk` | Android 8.0+ (API 26+) | `arm64-v8a` | **Recommended** |
| `PRVN-Music-v0.15-universal.apk` | Android 8.0+ (API 26+) | Universal (All ABIs) | Available |
| Desktop JAR / Native | Windows / Linux / macOS | x86_64 / aarch64 | JVM Target Available |

### Installation Note
When installing the APK update:
- If updating from an earlier debug test build, Android requires uninstalling the previous debug build due to signature continuity protection (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- The in-app updater detects signature matching automatically before installation.

---

## Architecture & Open Source Attribution
PRVN Music is built using **Kotlin Multiplatform** and **Compose Multiplatform**. It builds upon the open-source foundation of [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by Tuan Minh Nguyen Duc (maxrave-dev) licensed under **GNU General Public License v3.0 (GPL-3.0)**.
