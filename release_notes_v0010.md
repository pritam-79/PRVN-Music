# PRVN Music v0.0.10 (Build 13) Release Notes

### 🛡️ Critical Crash Repair & Stability Release

#### 1. Fixed App Force Close on Open & Play ("app os closed every time when i open and play any song")
- **Missing `@JvmStatic` JNI Link Error Resolved**: Added missing `@JvmStatic` annotation to `MseHostNative.setPreamp(handle, db)`. When songs were loaded or queues restored at launch, `applySurfaceAudio` called `setAudioEffect` on the main thread. Without `@JvmStatic`, ART failed to bind the static C export `Java_io_ymusic_mse_MseHostNative_setPreamp`, throwing an uncaught `UnsatisfiedLinkError` that forcibly closed the app.
- **Fail-Safe Native Call Wrapping**: Wrapped all audio DSP (`setEq`, `setBass`, `setVirtualizer`, `setPreamp`, `setVolume`, `pcmLevels`, `audioSessionId`, `videoFailure`) in `MseVideoSurface.android.kt` and `BindNativeMse.android.kt` with defensive try-catch handlers to ensure native errors never terminate the Android UI process.

#### 2. Media3 `DefaultAudioSink` Hardening
- **Zero-Divide Protection**: Guarded `playedFrames()`, `padToStart()`, `padToEnd()`, and `handleBuffer` against zero sample rates or zero audio channel counts during initial buffering phases.
- **Audio Looper Thread Crash Protection**: Enclosed `step()` inside `MseAudioOutput.kt` in a comprehensive error-recovery loop so unexpected exceptions never kill the dedicated `mse-audio` thread.

---
**Package Name**: `com.prvn.music`  
**Target Architecture**: Android ARM64 (`arm64-v8a`)  
**Base Foundation**: GPL-3.0 Attribution Preserved
