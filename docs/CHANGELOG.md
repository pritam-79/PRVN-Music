# PRVN Music — Changelog

All notable changes to the PRVN Music project are documented in this file.

---

## [0.0.2] — 2026-11-08

### Added
- **Play Together Realtime Backend Configuration**:
  - Added user settings in **Settings → Play Together** for Supabase Realtime endpoint (`wss://<project>.supabase.co/realtime/v1/websocket`) and anon public API key.
  - Added dynamic transparency banner in the Play Together tab indicating active transport mode ("Local Relay Mode (Offline / Preview)" vs "Supabase Realtime (Connected)").
- **Dual Backend Transport Engine**:
  - Live Phoenix Channels WebSocket client for sub-50ms synchronized playback events and presence tracking.
  - Automatic fallback to on-device simulated relay when offline or unconfigured.
- **Dynamic Launcher Icons**:
  - 5 customizable adaptive launcher icons switchable directly in Settings:
    1. PRVN Original (Classic Emerald Green)
    2. PRVN Purple (Signature Violet)
    3. PRVN Neon (Cyan Electric)
    4. PRVN Minimal (Monochrome Dark)
    5. PRVN DJ (Soundwave DJ)
  - Managed via dynamic Android `<activity-alias>` component enabling/disabling in `AppIconManager.kt`.
- **Modular Settings Redesign**:
  - Restructured into 8 organized categories: `APPEARANCE`, `PLAYBACK`, `PLAY TOGETHER`, `DOWNLOADS`, `NOTIFICATIONS`, `PRIVACY`, `ABOUT PRVN`, and `UPDATES`.
- **Unit Test Suite**:
  - Added `SyncMathTest.kt` verifying drift compensation math, clock delta calculations, and timestamp normalization.

### Changed
- Bumped application version name to `0.0.2` and Android version code to `2`.
- Updated release archives naming pattern to `PRVN-Music-0.0.2-arm64-v8a-release.apk`.
- Updated About PRVN screen with version `0.0.2 (Build 2)`.
- Replaced outdated external link redirects with standard YouTube URLs in `SongActions.kt`.
- Cleaned feedback reporting string to reference `PRVN Music`.

### Fixed
- Fixed compilation and type resolution issues with `settings.flow(...)` in Play Together screens.
- Fixed native JNI toolchain compilation flags for Android cross-compilation on Windows hosts.

---

## [0.0.1] — 2026-11-08

### Added
- Initial fork and rebranding as **PRVN Music**, targeting Android smartphone devices (`com.prvn.music`).
- Dark premium interface with signature purple accents (`#0B0E14` background, `#A855F7` accent).
- Integrated FFmpeg ARM64 native decoding libraries (`libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`, `libswscale.so`, `libavfilter.so`, `libffmpeg.so`).
- Full background playback with Android MediaStyle notifications and lock-screen controls.
- Play Together group listening UI and local room session manager.
- Open-source legal attribution and third-party license notices under GNU GPL v3.0.
