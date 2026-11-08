# PRVN Music — macOS Distribution Status

## Current Status: Development / Architecture Ready

PRVN Music's shared logic and Compose Multiplatform presentation layers are designed for cross-platform execution.

### macOS Requirements
- **Host System**: macOS 14+ (Sonoma or later) on Apple Silicon (ARM64) or Intel (x86_64).
- **Toolchain**: Xcode 15+, Apple Clang, and macOS SDK.
- **Audio Decoding**: FFmpeg universal dylibs for macOS.
- **Packaging**: macOS `.dmg` / `.app` bundle signed with Apple Developer ID.
