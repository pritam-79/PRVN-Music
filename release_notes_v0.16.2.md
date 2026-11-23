# PRVN Music v0.16.2 & iOS v0.01

## 🌟 Major Highlights

### 1. Complete Elimination of Unwanted Upstream Popups & Clean Branding
- **Zero Promotional Popups:** Completely purged `kotlin-footguns` star prompt dialog, upstream `ReviewDialog` (ProductHunt, Buy Me a Coffee, Twitter), and upstream blog RSS notification workers.
- **Official Developer Attribution:** Refreshed all attribution across Settings and About Us to **PRVN Music**, **Developed by Pritam** (GitHub: `https://github.com/pritam-79/PRVN-Music`).
- Cleaned string resources across all 30 supported languages.

### 2. Group Listening (Listen Together) — Advanced Room Permissions & Playback Sync Repair
- **3 Room Permission Tiers:**
  1. **Host Only (`host_only`):** Only the host can skip tracks, scrub/seek, or manipulate queue.
  2. **Everyone Can Control (`everyone`):** Every room member can collaborate and control playback seamlessly.
  3. **Approved Members (`approved`):** Host can individually grant or revoke playback control permissions per participant.
- **Fixed Synchronization Glitch:**
  - Resolved listener device automatic pause issue on host track transitions.
  - Eliminated restarting from 0:00 when listener resumes playback; guests now accurately synchronize with the host's live position via server clock compensation.
  - Active remote track loading protection prevents spurious sync drops during audio buffering.
  - Member badges indicate who has playback control access.

### 3. Universal Synchronization Engine
- Single unified account architecture synchronizing favorites, playlists, queue, listening history, and playback position across Android, iPhone, and Desktop.
- Offline-first cache with automatic conflict resolution.
- Active device arbitration ensures smooth handoff between devices.

### 4. PRVN Music for iPhone v0.01 (Build 1)
- Kotlin Multiplatform Compose UI integration for iOS.
- Native `AVAudioSession` configuration for background playback (`.playback` category with long-form audio policy).
- System lock-screen and Control Center integration via `MPNowPlayingInfoCenter` and `MPRemoteCommandCenter`.
- Audio interruption and route change handling (auto-pause on headphone disconnect).
- Ready for TestFlight and App Store distribution (`com.prvn.music`).

---

## 📦 Package Information
- **Application Name:** PRVN Music
- **Version Name:** `0.16.2` (Android) / `0.01` (iOS)
- **Version Code / Build:** `22` (Android) / `1` (iOS)
- **Application ID:** `com.prvn.music`
- **Signing Key:** Official PRVN Music release key (`72283ba993b3916281a55a34cb07ae7180486155f54f55963be9595f21a14aef`)
