# PRVN Music — Release Build Report

## Release v0.02 Verification Details

### 1. Build Metadata
- **Product**: PRVN Music
- **Version Name**: `0.0.2`
- **Version Code**: `2`
- **Application ID**: `com.prvn.music`
- **Compile SDK**: `36` (Android 16 Developer Preview)
- **Target SDK**: `35` (Android 15)
- **Minimum SDK**: `24` (Android 7.0 Nougat)
- **Primary Architecture**: `arm64-v8a`
- **Build Timestamp**: 2026-11-08

---

## 2. Generated Release Assets

| Artifact | Location | File Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- |
| **Android APK** | `Builds/PRVN-Music-v0.02-arm64.apk` | 9,790,544 bytes | `0A74F52EEA369BCFF5A0A0637BAF4A62AA55C71603217509EADACD62299301FF` |
| **Release Staging APK** | `PRVN-MUSIC-RELEASE/Android/PRVN-Music-Android.apk` | 9,790,544 bytes | `0A74F52EEA369BCFF5A0A0637BAF4A62AA55C71603217509EADACD62299301FF` |
| **Android App Bundle** | `androidApp/build/outputs/bundle/release/PRVN-Music-0.0.2-release.aab` | 18,782,688 bytes | `F52C0838F5DF3D6B0057D2D03D12471A8719D326802D910BBC2F9104967602BF` |
| **Release Staging AAB** | `PRVN-MUSIC-RELEASE/Android/PRVN-Music-Android.aab` | 18,782,688 bytes | `F52C0838F5DF3D6B0057D2D03D12471A8719D326802D910BBC2F9104967602BF` |

---

## 3. Cryptographic & Security Verification

### Android SDK `apksigner` Verification
```text
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
Signer #1 certificate DN: CN=PRVN Music, OU=Mobile, O=PRVN, L=Global, ST=Earth, C=US
Signer #1 certificate SHA-256 digest: 4a215589c372f440ee5b0e5ee761a66ff51eef35c36195e55bca7adffb49df3b
```

### Android SDK `aapt2 dump badging` Verification
- **Package**: `name='com.prvn.music' versionCode='2' versionName='0.0.2' compileSdkVersion='36'`
- **Application Label**: `PRVN Music`
- **Application Icon**: `res/mipmap-anydpi-v26/ic_launcher.xml`
- **Native ABI Filter**: `arm64-v8a`
- **Launchable Activity**: `io.ymusic.android.MainActivity`

---

## 4. Multi-Platform Artifact Staging Summary

- **Android**: Full release APK and Google Play AAB generated and staged in `PRVN-MUSIC-RELEASE/Android/`.
- **Windows**: UCRT64 GCC 16.2 and FFmpeg 9.0 toolchain configured and documented in `PRVN-MUSIC-RELEASE/Windows/`.
- **macOS / Linux / iOS**: Architecture and requirements documented in `PRVN-MUSIC-RELEASE/`.
