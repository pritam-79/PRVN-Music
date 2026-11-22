# PRVN Music v0.15 — Distribution Readiness Checklist

## Release Verification Checklist

| Dimension | Verification Item | Status | Technical Details |
| :--- | :--- | :--- | :--- |
| **Identity** | Product Name | **READY** | PRVN Music |
| **Identity** | Version Name | **READY** | `0.15.0` |
| **Identity** | Version Code | **READY** | `19` (Strict increment above v0.14.1 / Build 18) |
| **Identity** | Package ID | **READY** | `com.prvn.music` |
| **Updater** | Native In-App Engine | **READY** | Direct HTTPS download, real-time speed & ETA, pause/resume, cancel |
| **Updater** | Security Verification | **READY** | SHA-256 integrity, Package ID check, version continuity, signature extraction |
| **Updater** | UI Contrast | **READY** | Solid vibrant `#A855F7` and `#06B6D4` buttons with bold White/Black text |
| **Updater** | State Machine | **READY** | Full 16-state machine supported |
| **Branding** | Reference Logos | **READY** | All 17 official logos extracted, Minimal Neon primary, Liquid Glass special |
| **Branding** | Dynamic Launcher Switching | **READY** | 16 Activity Aliases + in-app picker in Settings |
| **Branding** | Receipt & Footers | **READY** | "PRVN MUSIC" on receipts, `@2026 PRVN Music v0.15.0\nPRVN` on all footers |
| **Security** | Signing Schemes | **READY** | Full multi-scheme enabled: v1, v2, v3, v4 |
| **Security** | Play Protect Hardening | **READY** | Documented in `PLAY_PROTECT_INVESTIGATION.md` |
| **Stability** | Tidal Rate Limits | **READY** | LRU caching, deduplication, circuit-breaker backoff, warning demotion |
| **Artifacts** | ARM64 Release APK | **PENDING BUILD** | `Builds/PRVN-Music-v0.15-arm64.apk` |
| **Artifacts** | Universal Release APK | **PENDING BUILD** | `Builds/PRVN-Music-v0.15-universal.apk` |
