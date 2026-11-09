# PRVN Music v0.0.9 (Build 12) Release Notes

### 🚀 Critical Playback & Play Together Fixes

#### 1. Zero-Stutter High Performance Playback (Normal & Listen Together)
- **Restored Media3 `DefaultAudioSink` Pipeline**: Reconnected `MseAudioOutput` to Media3's native `DefaultAudioSink` on a dedicated audio looper thread with real-time audio thread priority (`Process.THREAD_PRIORITY_AUDIO`).
- **Eliminated Native DAC Ring Buffer Stalls**: Connected audio output directly to `MseHostNative.dacAttach`, `dacPoll`, and `dacReport`. This drains the native C decode ring buffer continuously, preventing FFmpeg decode thread deadlocks (`ring_ms >= 400`) that caused playback to freeze, stutter, or fall silent.
- **Audio Session ID & FX Routing**: Integrated `onAudioSessionIdChanged` to bind Android audio effects and system equalizers cleanly.

#### 2. Play Together Room Discovery & Audio Fix
- **Robust Track & Queue JSON Parsing**: Fixed `GlobalRealtimeBackend.kt` track deserialization. When room states contain nested JSON objects for `track`, `currentTrack`, or `queue`, they are now properly deserialized into `JsonObject` instead of returning `null`. Friends can now join rooms and immediately load the active track and stream audio.
- **Seamless Room Join Retries**: Added automatic 600ms polling retries when resolving 4-digit room codes to prevent transient network misses.
- **Reliable Relay Communication**: Fixed ntfy.sh relay payload formatting to ensure JSON payload integrity across networks and Wi-Fi/mobile carrier transitions.

#### 3. Drift Compensation & Playback Stability
- **Host Authoritative Drift Protection**: Prevented unnecessary seeks on the host device. Only clients apply drift corrections.
- **Gentle Sync Window**: Expanded drift compensation threshold to 2,000ms with a 5-second cooldown to avoid micro-stuttering and continuous re-buffering.

---
**Package Name**: `com.prvn.music`  
**Target Architecture**: Android ARM64 (`arm64-v8a`)  
**Base Foundation**: GPL-3.0 Attribution Preserved
