# PRVN Music — Features & Capabilities

**PRVN Music** is a high-performance music streaming and local audio playback application developed with Kotlin Multiplatform, Jetpack Compose, and native C JNI engines.

---

## 1. Core Music Playback & Discovery

- **Online Streaming & Search**: Instant music search across extensive online catalogs powered by QuickJS JavaScript runtime and MediaSourceEngine (MSE).
- **Audio Quality Profiles**: User-selectable streaming bitrates:
  - *High* (320 kbps)
  - *Medium* (160 kbps)
  - *Low / Data Saver* (96 kbps)
- **Local Audio Library**: Automatic on-device MediaStore indexing, fast metadata scanning, and album art loading via integrated FFmpeg (`libffmpeg.so`).
- **Background Playback**: Seamless background audio playback through a dedicated foreground Android media service.
- **Media Controls**: Standard Android MediaStyle notification controls with skip, previous, pause/play, and lock-screen support.
- **Equalizer & Sound Effects**: Built-in 5-band graphic equalizer, virtualizer, bass boost, pitch shifting, and playback speed adjustment.
- **Offline Download Manager**: Background audio download manager with progressive MP3 and M4A extraction, pause/resume capability, and batch downloads.

---

## 2. Play Together (Synchronized Group Listening)

- **Dedicated Navigation**: One-tap access from the bottom navigation bar and drawer menu.
- **Custom 4-Digit Room Codes**: Instant room identification using user-chosen 4-digit numeric pins (e.g., `4821`).
- **Room Visibility**:
  - *Public*: Discoverable on the community active rooms lobby.
  - *Invite Only*: Joinable by direct invitation.
  - *Private*: Joinable only with the exact room code.
- **Control Modes**:
  - *Host Only*: Only the room creator controls playback and queue order.
  - *Collaborative*: Any joined listener can queue tracks and adjust playback.
- **Synchronized Playback Engine**: Real-time position tracking with drift compensation threshold (`<500ms`), YouTube-only stream guard, queue synchronization, and mid-song catch-up on join.
- **Dual Backend Support**:
  - *Live Supabase Realtime*: Connects over Phoenix Channels WebSockets (`wss://<project>.supabase.co/realtime/v1/websocket`) configured in **Settings → Play Together**.
  - *Local Relay Fallback*: Runs on-device local relay when offline or unconfigured, complete with an honest transparency badge.

---

## 3. UI/UX & Customization

- **Signature Theme**: Premium dark theme with deep background (`#0B0E14`), neon purple accents (`#A855F7`), and cyan highlights (`#06B6D4`).
- **5 Adaptive Launcher Icons**:
  1. *PRVN Original*: Classic emerald green.
  2. *PRVN Purple*: Signature dark violet.
  3. *PRVN Neon*: Electric cyan glow.
  4. *PRVN Minimal*: Monochrome dark slate.
  5. *PRVN DJ*: Soundwave DJ edition.
- **Redesigned Settings**: 8 structured categories:
  1. `APPEARANCE`: Theme (`Dark`, `AMOLED`, `Light`), App Icon selector, Accent Color, Language.
  2. `PLAYBACK`: Streaming Quality, Pitch/Speed, Equalizer, PiP, Audio Focus, SponsorBlock, Region.
  3. `PLAY TOGETHER`: Default Visibility, Allow Invites, Sync Drift Tolerance, Supabase Backend Config.
  4. `DOWNLOADS`: Download directory, format presets, queue limits.
  5. `NOTIFICATIONS`: Foreground playback notification status, Room alerts.
  6. `PRIVACY`: Crash reporting and usage statistics toggles.
  7. `ABOUT PRVN`: Application identity, versioning, GPL-3.0 attribution, and OSS licenses.
  8. `UPDATES`: Automatic background download toggle and direct link to GitHub Releases.
