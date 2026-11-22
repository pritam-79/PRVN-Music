## PRVN Music v0.14.0 (Build 18) — In-App Updater & Error Resolution Release

PRVN Music v0.14.0 Build 18 resolves critical playback metadata rate-limiting, Spotify token session recovery, listening receipt identity, Listen Together queue voting, and introduces a full native In-App Updater.

---

### What's Changed in Build 18

#### 1. Native In-App Updater & Installer
* **Direct Background Downloading:** Streams APK assets directly inside the app with real-time download progress bar, byte counters, and percentage indicators.
* **Seamless Android Package Installation:** Automatically triggers the Android package installer via `FileProvider` (`com.prvn.music.FileProvider`) using `REQUEST_INSTALL_PACKAGES` permission upon download completion.
* **Multiplatform Update Dialog:** Full Compose Multiplatform UI displaying release tag, markdown changelog, "Download & Install", "Open in Browser", and "You're Up to Date" confirmation dialogs.

#### 2. Critical Error Resolution (44 Errors Fixed)
* **Tidal HTTP 429 Prevention:**
  * Implemented thread-safe in-memory LRU cache (`tidalMetadataCache`) up to 200 items.
  * Added in-flight request deduplication (`tidalInFlightRequests`) for concurrent queue prefetching.
  * Added circuit-breaker backoff with exponential retry timing ($\min(30 \times 2^{n-1}, 600)$s).
  * Demoted non-critical audio analysis failures to warning level in `StreamRepositoryImpl`.
* **Spotify OAuth Session Guard:**
  * Added validation guard in `SpotifyAuth` and `LogInViewModel` rejecting blank `sp_dc` cookies without firing redundant network requests.
  * Updated Spotify Canvas user-facing branding to PRVN Music.

#### 3. Real Listening Receipt & Personal Identity
* **Removed Placeholder Mock Data:** Eliminated mock orders, Brazilian server references, fake credit card numbers, and authorization codes.
* **Authentic Local Listening Metrics:** Integrated real stats from local tracking (Top Track, Top Artist, Songs Played, Total Listening Time).
* **Editable Display Name:** Added custom Display Name setting with fallback to "Music Listener".
* **Privacy Sharing Controls:** Added `showName` and `showListeningTime` toggles in the receipt sharing sheet.

#### 4. Listen Together Community Queue Voting
* **Democratic Queue:** All room participants can view and vote on proposed track suggestions.
* **Real-Time Voting:** Dynamic upvote counters and automatic re-ordering by descending vote tallies.
* **Automatic Dequeue:** Automatically dequeues the winning candidate when the active song finishes.

---

### Release Binaries & Checksums

| Package | Architecture | Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- |
| `PRVN-Music-0.14.0-arm64-v8a-release.apk` | ARM64 (`arm64-v8a`) | 30,505,890 bytes | `0a44e2e5f08dfb5e74a39844b2b80b9b2e067353085fd6383257bb72f3d1008b` |
| `PRVN-Music-0.14.0-universal-release.apk` | Universal (All Archs) | 58,318,841 bytes | `fadb00cb3c29d0f609503c5a6b5de34f0e5103714e7d51582b47499df0bc52b2` |
