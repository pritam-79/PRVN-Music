# PRVN Music v0.14 — Error Audit Report

**Date:** November 22, 2026  
**Target Release:** v0.14.0  
**Version Code:** 18  
**Application ID:** `com.prvn.music`  
**Git Branch:** `v0.14-staging`  

---

## 1. Executive Summary

This audit investigates the application runtime errors, rate limits, identity inconsistencies, and branding leaks observed during the testing of PRVN Music v0.14. Specifically, the application log exported 44 consecutive error entries dominated by HTTP 429 rate limits from Tidal metadata requests, alongside issues in Spotify OAuth session recovery, user identity presentation on listening receipts, and upstream author branding.

All identified issues have been classified, root causes documented, and architectural fixes implemented on `v0.14-staging`.

---

## 2. Classification of Errors & Issues

### Category A: Playback & Streaming Metadata (Critical — 44 Logged Errors)
- **Observed Symptom:** Application log (`AppLog`) flooded with 44 red error entries:
  ```
  Tidal metadata error: Client request(GET https://tidal.com/v2/client-search/?query=...&types=TRACKS&limit=5&countryCode=US&locale=en_US&deviceType=BROWSER&includeContributors=true&supportsUserData=true) invalid: 429 . Text: ""
  ```
- **Source Files:**
  - `core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/YouTube.kt`
  - `core/data/src/commonMain/kotlin/com/maxrave/data/repository/StreamRepositoryImpl.kt`
- **Root Cause:**
  1. Tidal search endpoints (`https://tidal.com/v2/client-search/`) are used to fetch optional track audio analysis (BPM, musical key) for AutoMix and DJ-style audio transitions.
  2. Every time a track played or was loaded into queue, a new un-throttled HTTP GET was dispatched with zero local memory caching.
  3. Concurrent queue loads created multiple in-flight duplicates of the exact same query.
  4. Once Tidal returned HTTP 429 (Too Many Requests), the app continued retrying immediately without respecting the `Retry-After` header or applying exponential backoff.
  5. `StreamRepositoryImpl.kt` logged this non-critical enrichment step with `Logger.e` instead of `Logger.w`, turning benign metadata misses into 44 user-visible red errors in `AppLog`.

---

### Category B: Spotify Integration & Token Refresh (High)
- **Observed Symptom:** Infinite loop of `Spotify: token refresh failed: Unsuccessful token request` when entering Spotify settings with an empty cookie.
- **Source Files:**
  - `core/service/spotify/src/commonMain/kotlin/com/maxrave/spotify/auth/SpotifyAuth.kt`
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/LogInViewModel.kt`
- **Root Cause:**
  1. `refreshToken(spDc)` fired even when `sp_dc` was blank or empty.
  2. `LogInViewModel` set `_spotifyStatus.value = true` without validating the cookie payload.
  3. Canvas string still referenced upstream name ("Show Spotify Canvas in SimpMusic").

---

### Category C: Identity & Listening Receipt (Medium)
- **Observed Symptom:**
  - Receipt card displayed placeholder mock text: `ORDER #0003 FOR BRAZIL SERVER`, `CARD #: **** **** **** 2026`, `AUTH CODE: 625958`, `CARDHOLDER: BRAZIL SERVER`, barcode with `simpmusic.org`.
  - No editable user display name.
- **Source Files:**
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/home/analytics/ListeningReceipt.kt`
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/AnalyticsViewModel.kt`
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SettingsViewModel.kt`
- **Root Cause:**
  - Hardcoded fake transaction receipt metadata rather than real listening statistics.

---

### Category D: Listen Together Collaboration & Queue Voting (Feature Parity)
- **Observed Symptom:** Listen Together queue only allowed host approvals; no community member voting or democratic sorting.
- **Source Files:**
  - `core/service/listenTogether/src/commonMain/kotlin/org/simpmusic/listentogether/ListenTogetherSession.kt`
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/home/ListenTogetherScreen.kt`

---

### Category E: Upstream Attribution & User-Facing Branding (Medium)
- **Observed Symptom:**
  - Settings -> About Us showed `Author: maxrave-dev`, `@maxrave_dev`, developer blogs, buy me a coffee.
  - Footers showed `@2026 SimpMusic v0.14.0 maxrave-dev`.
  - Deep link filters missing `prvnmusic://`.
- **Source Files:**
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/component/EndOfPage.kt`
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/home/SettingScreen.kt`
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/other/CreditScreen.kt`
  - `androidApp/src/main/AndroidManifest.xml`
