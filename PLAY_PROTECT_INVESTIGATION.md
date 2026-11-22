# PRVN Music v0.15 — Google Play Protect & OEM Scanner Technical Investigation

## Executive Summary
This document provides a technical root-cause investigation into why sideloaded builds of PRVN Music (specifically build v0.14.0 / Build 18) trigger Google Play Protect warnings ("Unrecognized app / High risk / Unsafe app blocked") and aggressive OEM scanner interceptions (Xiaomi MIUI Security, Samsung Knox / McAfee, BBK/Vivo iManager).

The investigation analyzed:
1. APK Signing Scheme envelopes and certificate X.509 metadata using `apksigner.bat verify --verbose --print-certs`.
2. High-privilege Android permissions declared in `androidApp/src/main/AndroidManifest.xml`.
3. Play Protect heuristics for unverified sideloaded packages.
4. Upstream telemetry, crash-reporting SDKs, and network security policies.

---

## 1. Technical Root Causes

### Root Cause 1: Debug Keystore Signature (`CN=Android Debug`)
Running `apksigner` against `PRVN-Music-0.14.0-arm64-v8a-release.apk` yielded:
```
Signer #1 certificate DN: CN=Android Debug, O=Android, C=US
Signer #1 key algorithm: 2048-bit RSA
Signer #1 digest algorithm: SHA-256
Signer #1 signature algorithm: SHA256withRSA
```
**Analysis:** In `androidApp/build.gradle.kts`, `buildTypes.release` had `signingConfig = signingConfigs.getByName("debug")`.
`CN=Android Debug, O=Android, C=US` is the publicly documented default certificate created by the Android SDK (`~/.android/debug.keystore`). The private key is universally known (`android`). 

**Threat Assessment by Scanners:**
Google Play Protect, Samsung Knox, and Vivo iManager immediately flag any app signed with a public debug certificate as **Unsafe / High Risk Malware** because malicious repackaged applications frequently use standard debug keys. Debug certificates must never be used for public distribution.

### Root Cause 2: Incomplete APK Signature Scheme Envelopes
The `apksigner` output showed:
```
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v4 scheme (APK Signature Scheme v4): false
```
**Analysis:**
- Missing **v1 scheme (JAR signing)** causes installation failures or severe signature warnings on devices running older Android versions or custom OEM recovery installers.
- Missing **v3 scheme** prevents key rotation support introduced in Android 9 (API 28).
- Modern Play Protect verification requires full multi-scheme integrity (v1 + v2 + v3 + v4).

### Root Cause 3: High-Privilege Permissions Paired with Debug Key
The manifest requests several high-privilege permissions:
- `android.permission.REQUEST_INSTALL_PACKAGES` (allows the app to install other APKs / self-update).
- `android.permission.RECORD_AUDIO` (used for audio visualizer / equalizer / song recognition).
- `android.permission.CAMERA` (used for Desktop QR-code login sync).
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` (for background audio).

**Analysis:**
When an app declaring `REQUEST_INSTALL_PACKAGES` and `RECORD_AUDIO` is signed with `CN=Android Debug`, heuristic scanners categorize it under **Dropper / Spyware heuristics**. Sideloaders requesting package installation privileges are subject to maximum scrutiny.

### Root Cause 4: Lack of Play Console Developer Identity
Google Play Protect maintains a global telemetry database of known developer certificates. Any application sideloaded outside the Google Play Store signed with a certificate unseen by Google's cloud will trigger a generic warning ("Unrecognized app / Developer unknown").
However:
- Sideloaded with **Debug Certificate**: Triggers **Red Alert / Blocked Installation / Dangerous Application**.
- Sideloaded with **Production Keystore + Full Schemes (v1-v4)**: Triggers **Neutral Notice ("Unrecognised app — Install anyway")**, which is standard behavior for all F-Droid and GitHub open-source releases (such as NewPipe, Revanced, SimpMusic).

---

## 2. Hardening Measures Implemented in v0.15

1. **Multi-Scheme Signing Configuration:**
   `androidApp/build.gradle.kts` now enables full `v1`, `v2`, `v3`, and `v4` signing:
   ```kotlin
   enableV1Signing = true
   enableV2Signing = true
   enableV3Signing = true
   enableV4Signing = true
   ```
2. **Dedicated Production Release Keystore Pipeline:**
   Configured support for production signing credentials via environment variables:
   - `PRVN_KEYSTORE_FILE`
   - `PRVN_KEYSTORE_PASSWORD`
   - `PRVN_KEY_ALIAS`
   - `PRVN_KEY_PASSWORD`
   With graceful fallback to multi-scheme debug signing for local development.

3. **In-App APK Verification Engine:**
   Before launching any package installation, the native updater verifies:
   - SHA-256 integrity match.
   - Package ID matches `com.prvn.music`.
   - Target `versionCode >= installedVersionCode`.
   - Signing certificate matches current installation (preventing `INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
   - Checks `canRequestPackageInstalls()` and guides the user cleanly.

4. **Network Security Config:**
   Strict HTTPS-only communications across all endpoints, preventing plaintext traffic interception.
