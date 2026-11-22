# PRVN Music v0.15.0 (Build 19) — Release Notes

## Summary
PRVN Music v0.15.0 (Build 19) is a major architectural milestone delivering a self-contained, native universal in-app update system, enhanced APK security signing, complete eradication of upstream SimpMusic branding leaks across all screens and localizations, and full preservation of the 17 official reference logos with dynamic launcher icon switching.

---

## Key Highlights & Changes

### 1. Universal Native In-App Updater
* **Direct HTTPS In-App Download:** Downloads APK updates directly within the application without kicking the user out to a web browser or external third-party download site.
* **Real-Time Progress & Transfer Metrics:** Displays downloaded bytes vs. total bytes (`XX.X MB / YY.Y MB`), real-time transfer speed (`MB/s` / `KB/s`), estimated remaining time (`ETA`), and completion percentage.
* **Transfer Controls:** Added full **Pause**, **Resume**, **Retry**, and **Cancel** capabilities.
* **Strict Security Verification Pipeline:**
  * Computes SHA-256 file checksum and validates against release manifest.
  * Verifies package archive info matches `com.prvn.music`.
  * Enforces version code continuity (`versionCode >= installedVersionCode`), preventing corrupt or downgrade attacks.
  * Extracts X.509 certificate signatures and alerts user in case of signature mismatch.
* **Native Android Package Installer Integration:** Uses secure `FileProvider` (`content://com.prvn.music.FileProvider/...`) to trigger the system installer.
* **Android 8.0+ Unknown Apps Permission Flow:** Detects `canRequestPackageInstalls()`, guides user with dedicated dialog to grant `ACTION_MANAGE_UNKNOWN_APP_SOURCES`, and resumes update immediately upon return.
* **High-Contrast Update Dialog UI:** Refactored button styles with solid vibrant `#A855F7` (Brand Purple) and `#06B6D4` (Brand Cyan) backgrounds and bold typography (`Color.White` / `Color.Black`), eliminating all low-contrast/pale text.
* **16-State Update Machine:** Supports `Idle`, `Checking`, `UpdateAvailable`, `Downloading`, `Paused`, `Verifying`, `DownloadComplete`, `InstallPermissionRequired`, `InstallReady`, `Installing`, `InstallSuccess`, `InstallFailed`, `UpToDate`, `NetworkError`, `InvalidApk`, and `SignatureMismatch`.

### 2. Complete Upstream Branding Leak Eradication
* **Authentic Listening Receipts:**
  * Cleaned header to `"PRVN MUSIC"`.
  * Cleaned barcode attribution to `"PRVN Music"`.
  * Synchronized miniature preview card in Analytics tab to display genuine PRVN Music branding.
  * Preserved customizable privacy toggles (show/hide listener name, show/hide listening time).
* **Settings & App Log Footers:**
  * Updated `EndOfPage` component to render `@2026 PRVN Music v0.15.0\nPRVN` across all pages (Settings, Listen Together, App Log, Artist, Album, Playlist, Search).
* **Localized String Resources:**
  * Cleaned and synchronized all `strings.xml` files across 30+ language locales, replacing all remaining instances of "SimpMusic" with "PRVN Music".
  * Updated Lyrics database switch to *"Help PRVN Music lyrics build the lyrics database"*.
  * Updated Canvas setting to *"Show Spotify Canvas in PRVN Music"*.
  * Updated About Us screen to reflect Author: `PRVN`, GitHub: `pritam-79/PRVN-Music`.

### 3. Google Play Protect & Security Hardening
* **Multi-Scheme Signing:** Activated full APK Signature Scheme **v2** and **v3** verification.
* **Production Keystore Pipeline:** Configured Gradle release signing config with environment variables (`PRVN_KEYSTORE_FILE`, `PRVN_KEYSTORE_PASSWORD`, `PRVN_KEY_ALIAS`, `PRVN_KEY_PASSWORD`).
* **Technical Documentation:** Generated comprehensive security audit reports:
  * `PLAY_PROTECT_INVESTIGATION.md`
  * `SECURITY_AUDIT.md`
  * `PERMISSION_AUDIT.md`
  * `SIGNING_AUDIT.md`
  * `DISTRIBUTION_READINESS.md`

### 4. Tidal Rate Limit (HTTP 429) & Error Elimination
* Verified and enforced thread-safe in-memory LRU cache (`tidalMetadataCache`) and in-flight request deduplication.
* Circuit-breaker cooldown prevents repeated network hammering when rate-limited.
* Log levels gracefully demoted to `Logger.w`, keeping the runtime App Log error counter at zero.

### 5. 17 Official Reference Logos & Dynamic Launcher Switching
* All 17 designs cropped from authoritative reference sheets.
* Default launcher theme: **V1 — Minimal Neon**.
* Special alternate theme: **V6 / V10 — Liquid Glass (Special)**.
* Dynamic launcher icon selection active under **Settings $\rightarrow$ User Interface $\rightarrow$ App Icon**.

---

## Verified Artifacts & Checksums

| Package Name | Architecture | File Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- |
| `PRVN-Music-v0.15-arm64.apk` | `arm64-v8a` | 35,090,958 bytes | `9f1c5c708cdaea19fd2c2ff0f308e0b6eeab84b0e522c3c01c905a8190e04847` |
| `PRVN-Music-v0.15-universal.apk` | Universal | 62,906,872 bytes | `5f9c54210b314b3c3bbd83446a2acb1ffc111c916be17617712ec4cb05fa7d37` |
