# PRVN Music — Changelog

All notable changes to the PRVN Music project are documented in this file.

---

## [0.0.3] — 2026-11-08

### Major Highlights & Playback Repair
- **Android 14/15 Foreground Services Compliance**: Upgraded `PlaybackService` and `DownloadForegroundService` to use `ServiceCompat.startForeground` with explicit foreground service types (`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` and `FOREGROUND_SERVICE_TYPE_DATA_SYNC`), resolving runtime `ForegroundServiceTypeException` crashes on API 34+.
- **MSE Audio Buffer Hardening**: Raised `LOAD_TIMEOUT_MS` in `MsePlayer.kt` from 5,000 ms to 20,000 ms to eliminate initial audio chunk buffer timeouts on variable network connections.
- **Listen Together 6-Tab Architecture**:
  - **Now Playing**: Album artwork, live seekbar, synchronized host controls, and peer drift monitoring.
  - **Shared Queue**: Collaborative queue with host reordering (Move Up / Down) and track removal.
  - **In-Room Search**: Search YouTube catalog directly within the room to append tracks or submit requests.
  - **Song Request System**: Complete state machine (`PENDING`, `APPROVED`, `REJECTED`) with notification badges and host approval controls.
  - **Listeners List**: Real-time participant roster showing clock drift and host status.
  - **Room Settings**: Switch between `HOST_ONLY` and `COLLABORATIVE` control modes, toggle song requests, and set room password protection.
- **Supabase Realtime Production Schema**: Generated and verified `supabase_playtogether_schema.sql` supporting rooms, members, song requests, RLS policies, and sub-50ms channel broadcasts.
- **Release Packaging**:
  - Android ARM64 Release APK: `PRVN-Music-v0.03-arm64.apk` (`9,815,272` bytes, SHA-256: `04AC358894B51E18E5687B5C50EB0117EC2961ED4683957D0B056A69F901FFE9`).
  - Google Play AAB Bundle: `PRVN-Music-v0.03.aab` (`18,853,083` bytes, SHA-256: `264B0478FCCA8DD491FC16D23394F15AB45F7DE724900399487C18062FEE551E`).

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
