# PRVN Music v0.14 — Error Fix Report

**Date:** November 22, 2026  
**Target Release:** v0.14.0  
**Version Code:** 18  
**Application ID:** `com.prvn.music`  
**Git Branch:** `v0.14-staging`  

---

## 1. Architectural Fixes & Implementation Details

### Fix 1: Tidal HTTP 429 Prevention, In-Memory LRU Cache, and Circuit Breaker
- **File:** `core/service/kotlinYtmusicScraper/src/commonMain/kotlin/com/maxrave/kotlinytmusicscraper/YouTube.kt`
- **Implementation:**
  1. **Thread-Safe In-Memory LRU Cache (`tidalMetadataCache`):** Caches successful responses (up to 200 items) keyed by normalized track title and artist. Subsequent requests for the same track return instantaneously without hitting Tidal network endpoints.
  2. **In-Flight Request Deduplication (`tidalInFlightRequests`):** If multiple components request metadata for the same track concurrently (e.g. queue prefetching), subsequent callers await the first call's Deferred promise rather than opening duplicate network sockets.
  3. **Circuit-Breaker Backoff (`tidalRateLimitUntil`, `tidalConsecutive429s`):** When an HTTP 429 is encountered, the scraper inspects the HTTP `Retry-After` header. If absent, an exponential backoff formula is computed:
     $$\text{backoffSeconds} = \min(30 \times 2^{\text{consecutive} - 1}, 600)$$
     While the circuit-breaker is tripped, calls immediately return `null` without making network requests.
  4. **Log Level Demotion:** `StreamRepositoryImpl.kt` now logs Tidal failures as `Logger.w` instead of `Logger.e`. Audio playback proceeds unimpeded because BPM/key is strictly optional metadata.

---

### Fix 2: Spotify Auth Validation & Guard
- **Files:**
  - `core/service/spotify/src/commonMain/kotlin/com/maxrave/spotify/auth/SpotifyAuth.kt`
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/LogInViewModel.kt`
- **Implementation:**
  1. `refreshToken(spDc)` immediately aborts and returns failure if `spDc.isBlank()`.
  2. `LogInViewModel` validates cookie presence before transitioning auth state to true.
  3. String updated to: "Show Spotify Canvas in PRVN Music".

---

### Fix 3: Real Listening Receipt & Personal Identity
- **Files:**
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/home/analytics/ListeningReceipt.kt`
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/AnalyticsViewModel.kt`
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SettingsViewModel.kt`
- **Implementation:**
  1. Stripped all mock credit card and fake transaction fields.
  2. Header set to `PRVN MUSIC`, footer set to `PRVN Music`.
  3. Bound real analytics metrics: Top Track, Top Artist, Total Songs Played, Total Listening Duration.
  4. Added `DisplayName` preference with fallback to "Music Listener".
  5. Added `showName` and `showListeningTime` toggles in `ShareReceiptSheet` for complete user privacy when sharing receipts to social platforms.

---

### Fix 4: Listen Together Democratic Voting Queue
- **Files:**
  - `core/service/listenTogether/src/commonMain/kotlin/org/simpmusic/listentogether/ListenTogetherSession.kt`
  - `core/domain/src/commonMain/kotlin/com/maxrave/domain/data/model/listentogether/ListenTogetherModels.kt`
  - `core/data/src/commonMain/kotlin/com/maxrave/data/listentogether/ListenTogetherRepositoryImpl.kt`
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/ListenTogetherViewModel.kt`
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/home/ListenTogetherScreen.kt`
- **Implementation:**
  1. Extended `PendingSuggestion` with `votes: Set<String>`.
  2. Added `voteSuggestion(suggestionId, userId)`: toggles user vote, re-sorts suggestions by descending vote count so community favorites automatically rise to the top.
  3. Added `popWinningCandidate()`: dequeues the most-voted song into the active playback queue.
  4. Added Thumbs-Up voting icon button on every suggestion row with dynamic vote counters and host moderation overrides.

---

### Fix 5: Complete PRVN Music Branding & Identity
- **Files:**
  - `composeApp/src/commonMain/composeResources/values/app_name.xml`: `PRVN Music`
  - `composeApp/src/commonMain/composeResources/values/strings.xml`: All user-facing strings updated.
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/component/EndOfPage.kt`: Clean footer `@2026 PRVN Music v0.14.0 \nPRVN`.
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/home/SettingScreen.kt`: Author set to `PRVN`, GitHub row pointing to `pritam-79/PRVN-Music`.
  - `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/other/CreditScreen.kt`: Updated links to `https://github.com/pritam-79/PRVN-Music`.
  - `androidApp/src/main/AndroidManifest.xml`: Registered `prvnmusic://` custom scheme.
