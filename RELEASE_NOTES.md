# PRVN Music v0.13.0 — Release Notes

**Release date:** 2026-11-21  
**Version code:** 16  
**Package:** `com.prvn.music`  
**Architecture:** Android ARM64 (`arm64-v8a`)

---

## What's new in v0.13

This release is focused on making **Play Together** (synchronized group listening) reliable for
everyone — hosts and listeners alike. All four bugs reported through user feedback and screenshots
are fixed.

---

### 🛠️ Play Together reliability fixes

#### 1 — "Unable to reach Play Together server" false alarms
The app was showing the "Unable to reach" error after just 2 consecutive network hiccups. ntfy.sh
(the free-tier relay used by Play Together) occasionally returns rate-limit errors that clear up
on their own.

**Fix:** The app now waits for 5 consecutive failures before reporting the server as unreachable.
Retries are spread across up to 4 attempts with increasing wait times (0.5 s → 1.5 s → 3 s → 5 s)
and a small random jitter, so a brief blip no longer kicks everyone out of the lobby.

#### 2 — "Syncing · 63 406 ms" shown while a song is loading
Immediately after a new track started, the In-Room overlay displayed a large bogus drift value
(e.g. 63 406 ms) for a few seconds before settling. This was caused by the sync engine measuring
drift before the player had finished buffering the track.

**Fix:** The displayed drift counter is now held at **0 ms** until the player confirms the track
is ready. You will only ever see a real drift number once playback has actually begun.

#### 3 — PO-token errors (E60065) causing stream failures in-room
When YouTube's attestation token expired mid-room, the app gave up after one retry, leaving the
room stuck. 

**Fix:** The retry limit is raised to **3 attempts** with progressive delays between each. Most
transient token errors now recover automatically without any action needed.

#### 4 — Host stuck in "Reconnecting…" loop
On some network configurations, the host device entered a reconnecting state and never recovered,
even after the connection was restored.

**Fix:** The threshold for declaring a peer offline is raised from 3 to 6 consecutive missed
heartbeats. When reconnecting, the app now actively tries to fetch the latest room snapshot and
re-attaches — breaking the loop automatically.

---

### ⚙️ Build

- Upgraded to **v0.13.0** (internal build code 16).

---

## How to update

If you already have PRVN Music installed, open **Settings → Updates → Check for Updates** and
tap **Install** when prompted. The update is delivered as a direct in-app download.

---

*PRVN Music is built on [YMusic V4](https://github.com/ymusic-io/YMusicV4) (GPL-3.0).*
