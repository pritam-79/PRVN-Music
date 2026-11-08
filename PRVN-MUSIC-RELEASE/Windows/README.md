# PRVN Music — Windows Distribution Status

## Current Status: Development / Toolchain Staged

PRVN Music is architected using Kotlin Multiplatform (KMP) and Compose Multiplatform.
The primary production target is currently **Android (ARM64-v8a)**.

### Windows Desktop Requirements
- **Host Compiler**: Microsoft Visual C++ (MSVC) or MinGW-w64 GCC (MSYS2 UCRT64 `gcc 16.2.0` installed).
- **Audio Engine**: MSYS2 UCRT64 FFmpeg 9.0 (`libavcodec`, `libavformat`, `libswresample`).
- **JavaScript Engine**: QuickJS native JNI library compiled for Windows `x86_64` (`quickjs.dll`).
- **UI Framework**: Compose for Desktop (Skiko Windows runtime).

The Windows native audio decoding toolchain is currently installed via MSYS2 UCRT64. Host desktop package assembly will be delivered in upcoming milestone releases.
