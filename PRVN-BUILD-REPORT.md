# PRVN Music — Release Build Report

## Release v0.03 Verification Details

### 1. Build Metadata
- **Product**: PRVN Music
- **Version Name**: `0.0.3`
- **Version Code**: `3`
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
| **Android APK** | `Builds/PRVN-Music-v0.03-arm64.apk` | 9,815,272 bytes | `04AC358894B51E18E5687B5C50EB0117EC2961ED4683957D0B056A69F901FFE9` |
| **Release Staging APK** | `PRVN-MUSIC-RELEASE/Android/PRVN-Music-Android.apk` | 9,815,272 bytes | `04AC358894B51E18E5687B5C50EB0117EC2961ED4683957D0B056A69F901FFE9` |
| **Android App Bundle** | `androidApp/build/outputs/bundle/release/PRVN-Music-0.0.3-release.aab` | 18,853,083 bytes | `264B0478FCCA8DD491FC16D23394F15AB45F7DE724900399487C18062FEE551E` |
| **Release Staging AAB** | `PRVN-MUSIC-RELEASE/Android/PRVN-Music-Android.aab` | 18,853,083 bytes | `264B0478FCCA8DD491FC16D23394F15AB45F7DE724900399487C18062FEE551E` |

---

## 3. Cryptographic & Security Verification

### Android SDK `apksigner` Verification
```text
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
```

### Android SDK `aapt2 dump badging` Verification
- **Package**: `name='com.prvn.music' versionCode='3' versionName='0.0.3' compileSdkVersion='36'`
- **Application Label**: `PRVN Music`
- **Application Icon**: `res/BW.xml`
- **Native ABI Filter**: `arm64-v8a` (15 packaged .so DSOs)
- **Launchable Activity**: `io.ymusic.android.MainActivityOriginal`
