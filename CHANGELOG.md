# PRVN Music — Changelog

All notable changes to PRVN Music are documented here.  
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) — newest release on top.

---

## [0.13.0] — 2026-11-21

> **versionCode 16** · Build: v0.13.0

### Fixed — Play Together

- **"Unable to reach Play Together server" false alarms**: The offline detector now requires 5
  consecutive polling failures before reporting the server as unreachable (previously 2).
  Polling retries grow from 4 attempts (500 ms → 1 500 ms → 3 000 ms → 5 000 ms, ±20 % jitter)
  before giving up on a single cycle. Intermittent ntfy.sh 429/503 rate-limit spikes no longer
  show the error banner.

- **False "63 406 ms sync drift" display on stream load**: The sync-drift counter now shows
  `0 ms` while the player is still buffering or resolving a track. The displayed `syncOffsetMs`
  in the In-Room state is zeroed in `onTelemetryUpdate` whenever the player is buffering,
  not yet prepared, or in `RESOLVING` state, eliminating the misleading multi-second drift
  number that appeared immediately after a song started.

- **PO-token (E60065) failure loop**: The PO-token retry mechanism now attempts up to 3
  requests (previously 1) with progressive delays between each attempt, so transient YouTube
  attestation failures recover automatically without kicking the user out of the room.

- **"Reconnecting…" loop on host device**: The host offline threshold was raised from 3 to 6
  consecutive presence failures. After entering RECONNECTING state the heartbeat loop
  performs an active `fetchLatestRoomSnapshot` recovery attempt and re-attaches if the fetch
  succeeds, breaking the previous indefinite-reconnecting loop.

### Build

- Upgraded to **v0.13.0** (versionCode 16).

---

## [0.12.0] — 2026-11-20

> **versionCode 15** · Build: v0.12

### Fixed

- **Googlevideo PO token rejection (E60065)**: Fresh BotGuard session invalidation and capped
  automatic stream recovery prevent repeated E60065 token rejections during playback.
- **Play Together listener no-audio on join**: Granular stream-state machine ensures listeners
  correctly receive and play the host's stream when joining mid-song.
- **False −63 406 ms clock skew display**: Separated Network RTT from sync drift; the
  displayed delta is now capped and correctly attributed.
- **Home bottom navigation**: Tapping Home now reliably opens the PRVN Discover feed
  instead of doing nothing.
- **In-app update prompts**: Fixed spurious update dialogs appearing during auto-download;
  uncached update checks now complete correctly. Android notifications for app updates and
  Play Together song requests are delivered reliably.

---

## [0.11.0] — 2026-11-15

> **versionCode 14**

### Added

- **Play Together Song Request System**: Complete request state machine (`PENDING`, `APPROVED`,
  `REJECTED`) with notification badges and host approval controls.
- **Play Together Shared Queue**: Collaborative queue with host reordering (Move Up/Down) and
  track removal.
- **In-Room Search**: Search the YouTube catalog from inside a Play Together room to append
  tracks or submit requests.
- **Listeners List**: Real-time participant roster showing per-peer clock drift and host status.
- **Room Settings**: Switch between `HOST_ONLY` and `COLLABORATIVE` control modes, toggle
  song requests on/off, and set room password protection.
- **PRVN Auto-Radio**: Rooms never stall when the queue empties; continuous playback via
  curated popular-radio fallback with priority queueing for guest requests.

### Fixed

- Supabase Realtime production schema (`supabase_playtogether_schema.sql`) now includes RLS
  policies for rooms, members, and song requests with sub-50 ms channel broadcasts.
- Track and queue JSON parsing in `GlobalRealtimeBackend.kt` for nested JSON object payloads.
- ntfy.sh relay payload formatting for stability across Wi-Fi/mobile-carrier transitions.

---

## [0.10.0] — 2026-11-13

> **versionCode 13**

### Added

- **DSP Equalizer stability**: Added `@JvmStatic` to `MseHostNative.setPreamp()`; all audio DSP
  calls wrapped with defensive try-catch so a native error cannot crash the UI process.
- **Media3 DefaultAudioSink pipeline**: Reconnected `MseAudioOutput` on a dedicated
  real-time-priority audio looper thread, eliminating ring-buffer stalls and FFmpeg-decode
  deadlocks (previously manifested as 400 ms+ freeze/stutter).
- Zero-stutter playback in both normal and Play Together modes.

---

## [0.9.0] — 2026-11-12

> **versionCode 12**

### Added

- Zero-stutter high-performance playback (normal & Listen Together).
- Room discovery & audio fix for Play Together.
- Drift compensation & playback stability: gentle 2 000 ms window with 5 s cooldown.

---

## [0.5.0] — 2026-11-09

> **versionCode 5**

### Added

- Root-cause resolution of `Failed (E60033)` audio playback errors.
- Full-stack Desktop User-Agent synchronization and media proxy client integration.
- Status stream event isolation in `MsePlayer.kt`.
- In-app auto-update delivery (1-tap upgrade).

---

## [0.4.0] — 2026-11-09

> **versionCode 4**

### Added

- Playback error fix (`Failed (E60033)`): synchronized User-Agent with BotGuard PO token minting.
- Play Together continuous auto-radio & priority queueing.
- **Liquid Glass Theme Overhaul**: translucent obsidian-purple and cyber-cyan glass tokens.
- Cybernetic Neon Launcher Icon redesign.

---

## [0.3.0] — 2026-11-08

> **versionCode 3**

### Added

- Android 14/15 foreground-service compliance (`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`).
- MSE audio buffer hardening (`LOAD_TIMEOUT_MS` raised from 5 000 ms to 20 000 ms).
- Listen Together 6-tab architecture: Now Playing, Shared Queue, In-Room Search, Song Request,
  Listeners List, Room Settings.
- Supabase Realtime production schema (rooms, members, song requests, RLS policies).

---

## [0.2.0] — 2026-11-08

> **versionCode 2**

### Added

- Product rebrand: **PRVN Music** (`com.prvn.music`) with GPL-3.0 attribution.
- 5 dynamic adaptive launcher icons (Square, Rounded Square, Squircle, Circle, Teardrop).
- Settings redesign: compilation-warning cleanup, restored DSP Equalizer and speed-control dialogs.
- Play Together (synchronized listening) powered by Supabase Realtime + ntfy.sh offline relay.

---

## [0.1.0] — 2026-11-07

> **versionCode 1** · Initial Release

### Added

- Initial release of PRVN Music (Kotlin Multiplatform + Compose Multiplatform).
- Native ARM64 FFmpeg audio decoding pipeline.
- Material 3 dark-themed modern UI.
- Background playback and lock-screen media session integration.
