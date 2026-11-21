# PRVN Music v0.14 — Google Play Protect & Android Security Audit

**Target Version:** v0.14.0 (Build 14 / 16)  
**Package Name:** `com.prvn.music`  
**Reference Guidance:** [Google Play Protect Developer Guidance](https://developers.google.com/android/play-protect/warning-dev-guidance)  
**Audit Date:** November 2026  

---

## Executive Summary

Google Play Protect checks Android devices for Potentially Harmful Applications (PHAs), policy violations, and suspicious behaviors. For side-loaded and direct-distributed APKs outside the Google Play Store, Play Protect applies automated static and dynamic analysis.

This security audit reviews PRVN Music v0.14 against Google's official developer guidelines to ensure maximum security, transparency, and integrity without using evasive techniques.

> [!IMPORTANT]
> **No Evasion or Antivirus Bypass:** Play Protect must remain fully enabled. We do not implement code or obfuscation intended to bypass or hide from scanning. All resolutions adhere to legitimate Android security best practices.

---

## 1. APK Integrity & Signing Configuration

### 1.1 APK Signature Schemes (v1, v2, v3, v4)
- **Requirement:** Modern Android releases (Android 11+) require APK Signature Scheme v2 (Full APK Signature) or v3.
- **Audit Finding:** The release signing configuration applies both APK Signature Scheme v1 (JAR signing) and v2/v3 (APK Signing Block).
- **Key Continuity:** The existing signing keystore from v0.11/v0.12/v0.13 is strictly preserved. In-place upgrades retain cryptographic continuity.
- **Keystore Security:** Production signing keys and passwords remain externalized in local environment variables (`RELEASE_KEYSTORE_PATH`, `RELEASE_KEY_ALIAS`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_PASSWORD`) and are never checked into public Git repositories.

### 1.2 Checksum & Verification
- Release artifacts are hashed with SHA-256 and published alongside the build.
- Manifests (`update.json`) carry cryptographic SHA-256 hashes for every downloadable APK to prevent MITM tampering during update downloads.

---

## 2. Android Permissions Audit & Minimization

| Permission | Justification | Risk Level | Status |
|---|---|---|---|
| `android.permission.INTERNET` | Required for streaming audio and loading metadata | Normal | **Retained** |
| `android.permission.ACCESS_NETWORK_STATE` | Check connectivity for offline mode and bitrate switching | Normal | **Retained** |
| `android.permission.FOREGROUND_SERVICE` | Background audio playback service | Normal | **Retained** |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ typed foreground service for playback | Normal | **Retained** |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ typed foreground service for offline downloads | Normal | **Retained** |
| `android.permission.POST_NOTIFICATIONS` | Android 13+ runtime permission for playback & update alerts | Dangerous (Runtime) | **Prompted at runtime** |
| `android.permission.WAKE_LOCK` | Prevent CPU sleep during active audio playback | Normal | **Retained** |
| `android.permission.REQUEST_INSTALL_PACKAGES` | Required for in-app updater to trigger PackageInstaller | Special | **Directed to system settings** |
| `android.permission.READ_MEDIA_AUDIO` | Android 13+ access to local audio files | Dangerous (Runtime) | **Optional, runtime only** |
| `android.permission.READ_EXTERNAL_STORAGE` | Android 12 and below local audio file access | Dangerous (Runtime) | **Scoped (`maxSdkVersion=32`)** |
| `android.permission.WRITE_EXTERNAL_STORAGE` | Legacy file storage write | High | **REMOVED** (Replaced by scoped storage & app-specific directories) |
| `android.permission.RECORD_AUDIO` | Equalizer visualizer | Dangerous (Runtime) | **REMOVED / Optional** (Software-computed visualizer used instead) |
| `android.permission.CAMERA` | QR-code scanner for device login transfer | Dangerous (Runtime) | **Optional, runtime only** |

---

## 3. Component Export & IPC Security

### 3.1 Explicit `android:exported` Flags
All activities, services, broadcast receivers, and content providers define an explicit `android:exported` attribute:
- **`MainActivity`:** `android:exported="true"` (Launcher activity).
- **`PlaybackService`:** `android:exported="true"` with `android.media.browse.MediaBrowserService` intent-filter (required for Android Auto & System Media Controls). Protected by `androidx.media.session.MediaButtonReceiver`.
- **`DownloadService`:** `android:exported="false"` (Internal app service).
- **Internal Receivers:** `android:exported="false"`.

### 3.2 PendingIntent Immutability
All `PendingIntent` instances specify `PendingIntent.FLAG_IMMUTABLE` (or `FLAG_MUTABLE` where required by Android OS for MediaSession action buttons), preventing intent redirection hijacking on Android 12+ (API 31+).

---

## 4. Network Security & Cleartext Traffic

### 4.1 Strict HTTPS Enforcement
- Cleartext HTTP traffic is blocked by default (`android:usesCleartextTraffic="false"`).
- All network communication (YouTube Music, LRCLIB, Last.fm, GitHub updater, ntfy.sh relay) operates exclusively over TLS 1.3/1.2.
- Local streaming proxy (if used on `127.0.0.1` for MSE playback) is explicitly scoped to localhost via `res/xml/network_security_config.xml`.

---

## 5. Third-Party Dependencies & Supply Chain (SBOM)

- **Crashlytics / Telemetry:** FOSS build variant strips proprietary tracking SDKs. Production telemetry is opt-in or routed to self-hosted Sentry.
- **R8 / ProGuard Minification:** Release builds run full R8 shrinking and optimization, stripping unused classes, methods, and debug metadata.
- **Debuggable Flag:** Explicitly forced to `android:debuggable="false"` in release builds.

---

## 6. Google Play Protect Developer Guidance & Appeal Process

If Google Play Protect displays a warning on an unlisted or newly released APK:
1. **Analyze Detection Type:** Review whether the alert is generic ("Unrecognized App") or specific ("Potentially Harmful App"). Generic unranked warnings occur on new signing keys before reputation builds.
2. **Verify Package & Cert:** Ensure the certificate matches previous releases and no debug signing was used.
3. **Submit Developer Appeal:** If an erroneous warning appears, submit the release APK to Google's official review portal:
   - **Portal:** `https://support.google.com/googleplay/android-developer/contact/protectappeals`
   - **Details Provided:** App name, package ID, download URL, SHA-256 hash, and explanation that the app is an open-source music player distributed directly on GitHub.
