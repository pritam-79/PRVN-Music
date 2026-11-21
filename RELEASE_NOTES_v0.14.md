## PRVN Music v0.14.0 (Build 17) — Complete Compose Multiplatform Release

PRVN Music v0.14 is a complete architectural transformation and modern reimplementation powered by the official SimpMusic foundation, delivering genuine Compose Multiplatform support, PRVN dark branding, full feature parity, robust Listen Together real-time playback synchronization, and Google Play Protect readiness.

---

### What's Changed in v0.14.0

#### 1. In-App Updates & Notification Architecture
* **Fixed In-App Update Engine:**
  * Added explicit User-Agent: PRVN-Music to GitHub release API calls, preventing HTTP 403 Forbidden rejections.
  * Added resilient fallback to raw update.json manifest to guarantee uninterrupted updates even when GitHub unauthenticated API rate limits apply.
  * Corrected version semver comparator in VersionManager to prevent false update prompts when running the current release.
  * Fixed update download redirection in Compose UI directly to the official GitHub release page.
* **Fixed Android 13+ Notifications:**
  * Added dedicated **App Updates** notification channel (prvn_music_updates_channel), posting immediate system notifications when updates are detected.
  * Added EasyPermissions.onRequestPermissionsResult callback dispatch in MainActivity ensuring runtime permissions are properly acknowledged.
  * Added direct routing to system Notification Settings when granting permissions from the dialog.

#### 2. Critical Playback & Listen Together Resolution
* **Resolved Googlevideo PO Token Rejection (StreamProtectionStatus=3):** 
  * Room participants never exchange private googlevideo stream URLs or PO tokens over the network.
  * Only track identity and synchronization events are shared; each device independently resolves its own fresh, device-specific audio stream via StreamRepositoryImpl and kotlinytmusicscraper.
  * Automatic format expiration detection and silent background stream renewal (updateFormat).
* **Fixed Silent Guest in Play Together:**
  * Resolved the race condition where guest players paused on track change. Guests now correctly inherit room playing intent and start synchronized playback immediately upon joining.
* **Resilient MetroServer Protocol:**
  * Migrated transport to production-hardened MetroServer WebSocket protocol (wss://metroserverx.meowery.eu/ws), complete with configurable custom endpoints.
  * Monotonic clock drift calibration (ServerClock) ensures sub-second synchronization without audio stutter or endless seek loops.
  * Automatic exponential reconnection backoff (1s, 2s, 4s, 8s, 16s up to 5 attempts) with session token recovery.

#### 3. Modern Product Identity & UI/UX
* **PRVN Dark Branding:**
  * Deep Dark Background: #0B0E14
  * Accent Purple: #A855F7
  * Vibrant Cyan: #06B6D4
* **3 Player Styles:**
  * Classic Player
  * Material 3 Expressive Player
  * Apple Music-Inspired Player
* **Comprehensive Discovery:**
  * Home, Browse, Mix for You, Quick Picks, Charts, Trending, Podcasts, Moods, Genres, and New Releases.

#### 4. Advanced Audio & Media Capabilities
* **Audio Engineering:** 10-band equalizer, AutoEQ headphone correction profiles, reverb, crossfade, and AutoMix.
* **Synchronized Lyrics:** Word-by-word synchronized lyrics, Romanization, translation, full-screen and landscape lyrics modes.
* **Integrations:** SponsorBlock segment skipping, Return YouTube Dislike, Last.fm scrobbling, and Discord Rich Presence.
* **Android & Desktop Ecosystem:** Android Auto integration, Glance home screen widgets, Quick Settings recognition tile, and Desktop JVM distribution support.

#### 5. Google Play Protect Readiness
* Clean, non-evasive architecture adhering strictly to official Android SDK guidelines.
* Explicit Android 14/15 foreground service types (mediaPlayback, dataSync).
* Verified 
etwork_security_config.xml enforcing system CA trust without arbitrary user certificates.
* Zero obfuscated dynamic code execution (DEX reflection / eval).

---

### Release Binaries & Checksums

| Package | Architecture | Size | SHA-256 Checksum |
| :--- | :--- | :--- | :--- |
| PRVN-Music-0.14.0-arm64-v8a-release.apk | ARM64 (arm64-v8a) | 30,491,318 bytes | c97982b7c557a837fc4b8045a39496920e7a12850f8f61dc3eb05368717756a9 |
| PRVN-Music-0.14.0-universal-release.apk | Universal (all archs) | 58,304,269 bytes | 90f76f6126b5ef5593bb6b13dded7905f9a784720fbe6990427f20768316d11 |

### Upstream Attribution & Compliance
* Built in accordance with GNU General Public License v3.0 (GPL-3.0).
* Upstream foundation: [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by @maxrave-dev, [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) by TeamNewPipe, and [AutoEq](https://github.com/jaakkopasanen/AutoEq) by Jaakko Pasanen.
