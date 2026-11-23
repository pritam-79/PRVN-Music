# PRVN Music v0.16.1 (Build 21)

PRVN Music v0.16.1 is a critical quality and stability update focused on real-time group listening playback synchronization, About Us branding fixes, and expanded documentation.

---

### What's New in v0.16.1

- 👥 **Listen Together Playback Synchronization Repair:**
  - Resolved the glitch where listener devices automatically paused when the host changed songs.
  - Listeners now seamlessly transition and continue playing in real time when the host changes songs or starts playback.
  - Fixed host `publishPlayPauseAsHost` transient state reporting and extended track settle timeout to 5 seconds to accommodate network stream extraction.
  - Enhanced guest buffer barrier handling with adaptive polling to prevent room playback stalls.

- ⏱️ **Host-Aligned Position Resync on Resume:**
  - Fixed synchronization gap where guest resuming or tapping play started from `0:00` instead of aligning with the host's actual server-calibrated playhead.
  - Mapped server timestamp (`lastUpdate` / `capturedAtServerTime`) into room state snapshots to ensure sub-500ms sync precision.

- 🎨 **Official "About Us -> Version" Branding Refresh:**
  - Replaced legacy SimpMusic icon and details inside **Settings -> About Us -> Version** (`CreditScreen`) with the official PRVN Music **Minimal Neon (V1)** identity.
  - Updated developer attribution to **PRVN (pritam-79)** and updated GitHub repository links.
  - Refreshed in-app legal disclaimer and copyright metadata.

- 📖 **Comprehensive Group Listening Documentation:**
  - Documented real-time room capabilities in `README.md`, including 4-digit codes, custom WebSocket servers (`wss://`), collaborative queue upvoting, and host moderation controls.

---

### Package Downloads & Verification

| File Name | Architecture | SHA-256 Checksum |
| :--- | :--- | :--- |
| `PRVN-Music-v0.16.1-arm64.apk` | `arm64-v8a` | `c0a84b92d4cfc56efd017f528da3e1b741ec9dd6114d74385af23609f58c3373` |
| `PRVN-Music-v0.16.1-universal.apk` | Universal (All ABIs) | `e3dcbb11e19e86c353bffc3412ce4697b43dacab20433eeeefd2a5171313b7be` |

### Security & Signing
- **Package ID:** `com.prvn.music`
- **Version Name:** `0.16.1`
- **Version Code:** `21`
- **Certificate SHA-256:** `72283ba993b3916281a55a34cb07ae7180486155f54f55963be9595f21a14aef` (maintains seamless upgrade continuity)
