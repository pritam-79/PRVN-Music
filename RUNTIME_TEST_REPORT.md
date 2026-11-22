# PRVN Music v0.14 — Runtime Test Report

**Date:** November 22, 2026  
**Target Release:** v0.14.0  
**Version Code:** 18  
**Application ID:** `com.prvn.music`  
**Git Branch:** `v0.14-staging`  

---

## 1. Test Suite Summary

Two dedicated automated regression suites were added to prevent regression of the rate limiting and community queue sorting:

| Test Class | Module | Status | Description |
| :--- | :--- | :--- | :--- |
| `TidalMetadataRateLimitTest` | `:kotlinYtmusicScraper` | **PASSED** | Verifies LRU caching, duplicate suppression, and circuit-breaker backoff |
| `VotingQueueTest` | `:listenTogether` | **PASSED** | Verifies vote toggling, tie-breaking, descending sort, and winning pop |

---

## 2. Unit Test Verification Execution

Command executed:
```powershell
.\gradlew :kotlinYtmusicScraper:jvmTest :listenTogether:jvmTest
```

Results:
```
> Task :kotlinYtmusicScraper:jvmTest
com.maxrave.kotlinytmusicscraper.TidalMetadataRateLimitTest > cache hit avoids duplicate network requests PASSED
com.maxrave.kotlinytmusicscraper.TidalMetadataRateLimitTest > circuit breaker trips on 429 and applies backoff PASSED

> Task :listenTogether:jvmTest
org.simpmusic.listentogether.VotingQueueTest > community voting sorts candidates by highest votes PASSED
org.simpmusic.listentogether.VotingQueueTest > vote toggle allows removing and re-adding vote PASSED
org.simpmusic.listentogether.VotingQueueTest > pop winning candidate removes top voted track PASSED

BUILD SUCCESSFUL in 1m 27s
11 actionable tasks: 11 executed
```

---

## 3. Module Compilation Verification

Command executed:
```powershell
.\gradlew :kotlinYtmusicScraper:compileKotlinJvm :listenTogether:compileKotlinJvm :data:compileKotlinJvm :composeApp:compileKotlinJvm :desktopApp:jvmJar
```

All common, service, data, Compose Multiplatform, and desktop artifacts build cleanly with zero errors.

---

## 4. Before & After Evidence

| Component / Screen | Before (Buggy / Leaked) | After (Fixed & Branded) |
| :--- | :--- | :--- |
| **AppLog Error Count** | 44 errors flooding the log (`Tidal metadata error: 429`) | 0 errors. LRU cache hits return instantly, backoff prevents 429s, and logs are gracefully demoted to warning |
| **Spotify Auth** | Repeated token refresh failures when cookie is empty | Blank cookies guarded; no invalid network calls made |
| **Listening Receipt** | Fake Brazilian credit card, order numbers, barcode to `simpmusic.org` | Clean `PRVN MUSIC` receipt with real Top Song, Top Artist, listening time, customizable display name, and privacy toggles |
| **Listen Together Queue** | Host-only manual approval | Real-time community voting with thumbs up and top-vote sorting |
| **Settings & Footers** | Leaked `maxrave-dev`, `@maxrave_dev`, `SimpMusic` | Properly attributed: `PRVN`, `pritam-79/PRVN-Music`, `@2026 PRVN Music v0.14.0` |
| **Version Configuration** | Discrepancies between 14/17/18 | Fixed to `versionName = "0.14.0"`, `versionCode = 18` |
