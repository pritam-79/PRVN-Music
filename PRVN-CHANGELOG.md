# PRVN Music — Changelog

All notable changes to the PRVN Music project are documented in this file.

---

## [0.0.2] — 2026-11-08

### Major Highlights
- **Product Rebranding**: Independent product branding as **PRVN Music** (`com.prvn.music`), maintaining official GNU GPL-3.0 attribution for upstream YMusic V4.
- **Dynamic Adaptive Launcher Icons**: 5 launcher icon density configurations (Square, Rounded Square, Squircle, Circle, Teardrop) in neon purple (`#A855F7`) and cyber cyan (`#06B6D4`) with deep space dark theme (`#0B0E14`).
- **Settings Redesign**: Comprehensive settings architecture refactoring, eliminating compilation warnings, restoring seamless DSP Equalizer (`EqualizerFxSheet`) and playback speed control dialogs.
- **Play Together (Synchronized Listening)**:
  - Synchronized group listening powered by Supabase Realtime client.
  - Built-in resilient offline relay fallback allowing peer-to-peer room simulation even without active cloud configuration.
  - Real-time room creation, room joining by code, participant sync, host seek and play/pause synchronization.
- **Release Packaging**:
  - Full ARM64 release APK: `PRVN-Music-v0.02-arm64.apk` (`9,790,544` bytes).
  - Production Android App Bundle: `PRVN-Music-0.0.2-release.aab` (`18,782,688` bytes).
  - Cryptographic verification via Android SDK `apksigner` (APK Signature Scheme v2).

---

## [0.0.1] — 2026-11-07

### Initial Release
- Initial release of PRVN Music player based on Kotlin Multiplatform and Compose Multiplatform.
- Native ARM64 FFmpeg audio decoding pipeline.
- Material 3 dark-themed modern UI.
- Background playback and lockscreen media session integration.
