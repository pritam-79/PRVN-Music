# PRVN Music v0.15 — Signing & Cryptographic Audit

## Overview
This document outlines the cryptographic signing configuration, certificate management strategy, and Android APK Signature Scheme implementation for PRVN Music v0.15.

---

## 1. Signature Scheme Envelopes

Android supports four progressive APK signature schemes:
- **v1 (JAR Signing):** Signs individual ZIP entries and verifies against `META-INF/MANIFEST.MF`. Required for Android versions below 7.0 and older custom package managers.
- **v2 (APK Signature Scheme v2):** Protects the entire APK file integrity using a dedicated block inside the ZIP file. Introduced in Android 7.0 (API 24).
- **v3 (APK Signature Scheme v3):** Introduces signing certificate rotation and proof-of-rotation records in the signing block. Introduced in Android 9.0 (API 28).
- **v4 (APK Signature Scheme v4):** Generates streaming signature files (`.apk.idsig`) for fast streaming installs via ADB. Introduced in Android 11.0 (API 30).

### Configuration in `androidApp/build.gradle.kts`
```kotlin
signingConfigs {
    create("release") {
        val keystoreFile = System.getenv("PRVN_KEYSTORE_FILE") ?: project.findProperty("PRVN_KEYSTORE_FILE") as? String
        val keystorePassword = System.getenv("PRVN_KEYSTORE_PASSWORD") ?: project.findProperty("PRVN_KEYSTORE_PASSWORD") as? String
        val keyAlias = System.getenv("PRVN_KEY_ALIAS") ?: project.findProperty("PRVN_KEY_ALIAS") as? String
        val keyPassword = System.getenv("PRVN_KEY_PASSWORD") ?: project.findProperty("PRVN_KEY_PASSWORD") as? String

        if (!keystoreFile.isNullOrEmpty() && file(keystoreFile).exists()) {
            storeFile = file(keystoreFile)
            storePassword = keystorePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword
        } else {
            storeFile = signingConfigs.getByName("debug").storeFile
            storePassword = signingConfigs.getByName("debug").storePassword
            this.keyAlias = signingConfigs.getByName("debug").keyAlias
            this.keyPassword = signingConfigs.getByName("debug").keyPassword
        }
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = true
    }
}
```

---

## 2. Certificate Continuity & Upgrade Path

Android enforces strict signature continuity. An application cannot be upgraded without uninstalling if the signing key changes (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

### Upgrade Scenarios:
1. **Existing v0.14 Users (Signed with Local Debug Keystore):**
   - Fallback release builds signed with the consistent local keystore upgrade smoothly without requiring uninstallation.
2. **Transitioning to Dedicated Production Keystore:**
   - When deploying with a production keystore (`PRVN_KEYSTORE_FILE`), users upgrading from a debug-signed build must perform a one-time uninstall of the debug build.
   - The in-app updater explicitly detects this condition (`InAppUpdateState.SignatureMismatch`) and informs the user with instructions rather than failing silently.
