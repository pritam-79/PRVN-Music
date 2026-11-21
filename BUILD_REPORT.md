# PRVN Music v0.14 — Build Verification Report

**Date:** November 2026  
**Target Release:** v0.14.0  
**Staging Branch:** `v0.14-staging`  
**Reference Engine:** SimpMusic Compose Multiplatform Engine (`maxrave-dev/SimpMusic` @ v2.6.0)

---

## 1. Environment & Toolchain

- **JDK:** OpenJDK 21.0.13 (Eclipse Adoptium)
- **Gradle:** 9.8.1
- **Kotlin:** 2.4.21
- **Android Gradle Plugin (AGP):** 9.5.0
- **Compose Multiplatform:** 1.12.2
- **Host OS:** Windows 10 (10.0 amd64)

---

## 2. Compilation Results

### 2.1 Android Pipeline
- **Task:** `:androidApp:assembleDebug`
- **Result:** **`BUILD SUCCESSFUL` in 37m 23s** (313 tasks executed)
- **Output Artifacts:**
  - `androidApp-arm64-v8a-debug.apk` (71,920,642 bytes)
  - `androidApp-armeabi-v7a-debug.apk` (71,963,145 bytes)
  - `androidApp-universal-debug.apk` (101,560,617 bytes)
  - `androidApp-x86_64-debug.apk` (73,884,272 bytes)

### 2.2 Desktop JVM Pipeline
- **Task:** `:desktopApp:jvmJar`
- **Result:** **`BUILD SUCCESSFUL` in 11m 53s** (68 tasks executed)
- **Output Artifacts:**
  - `desktopApp-jvm-2.6.0.jar` (3,103 bytes)
  - `composeApp-jvm.jar` (12,749,453 bytes)
  - Desktop Native Engine: `libmpv` client API via JNA
