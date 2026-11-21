# PRVN Music v0.14 — Technical Migration Report

**From:** PRVN Music v0.13 (YMusic V4 KMP Architecture)  
**To:** PRVN Music v0.14 (SimpMusic Modern Compose Multiplatform Architecture)  
**Author:** PRVN Music Architecture Team  
**Date:** November 2026  
**License Compliance:** GNU General Public License v3.0 (GPL-3.0)

---

## 1. Architectural Evolution

```
[PRVN Music v0.10 - v0.13]                  [PRVN Music v0.14 Target]
  YMusic V4 Custom KMP Stack                  SimpMusic Compose Multiplatform Stack
┌───────────────────────────────┐           ┌───────────────────────────────────────────┐
│ UI: Custom Compose Multiplat  │           │ UI: Compose Multiplatform + Material 3    │
│ Engine: engine-js (QuickJS)   │   ====>   │ Player: Media3 / ExoPlayer + libmpv (JVM) │
│ DB: SQLDelight                │           │ DB: Room Database + DataStore             │
│ Network: kmpHttp Custom Stack │           │ Network: Ktor Client + Multiplatform KSP  │
│ Relay: ntfy.sh Global Relay   │           │ Relay: Metrolist Proto & ntfy.sh Hybrid   │
└───────────────────────────────┘           └───────────────────────────────────────────┘
```

---

## 2. Side-by-Side Comparison

| Architectural Domain | PRVN Music v0.13 (Previous) | PRVN Music v0.14 (SimpMusic Foundation) |
|---|---|---|
| **UI Framework** | Compose Multiplatform (legacy layout) | Compose Multiplatform + Material 3 Expressive + Liquid Glass |
| **Audio Engine** | Custom JS + C++ AudioTrack bridge | Android Media3 / ExoPlayer + Desktop libmpv (JNA) |
| **Player Styles** | Single player interface | 3 Styles: Classic, Material 3 Expressive, Apple Music-inspired |
| **Equalizer** | System EQ intent only | Native 10-band Equalizer + AutoEq Headphone Profiles + Reverb/Delay |
| **Lyrics** | Basic synchronized sheet | LRCLIB + BetterLyrics + Word-by-Word + Romanization & Translation |
| **Local Storage** | SQLDelight | Android Room Database + Jetpack DataStore Preferences |
| **External Services** | YouTube Music scraping | YouTube Music + SponsorBlock + Last.fm + Spotify Canvas |
| **Desktop Platform** | JVM fallback player | Full Desktop app (Windows, macOS, Linux) with floating capsule |
| **Car Integration** | Basic MediaBrowserService | Android Auto (`car-app 1.9.0`) + Home Screen Glance Widgets |
| **Listen Together** | Custom ntfy.sh JSON relay | Unified Metrolist Protocol + ntfy.sh fallback |

---

## 3. Module Mapping & Migration Manifest

### 3.1 Preserved Core Configurations
- **Application ID:** `com.prvn.music`
- **Signing Keys:** Debug and release keystores strictly preserved for update continuity.
- **Brand Palette:** Deep Dark (`#0B0E14`), Purple Accent (`#A855F7`), Cyan Accent (`#06B6D4`).
- **Updater System:** Direct GitHub raw `update.json` manifest checker with verified SHA-256 validation.

### 3.2 Migrated & Replaced Modules
```
PRVN Music v0.13 Module                 PRVN Music v0.14 Module
────────────────────────                ───────────────────────
androidApp                              androidApp (Media3 + Android Entry)
commonUi                                composeApp (Shared UI, Screens, ViewModels)
player                                  core/media/media3 + core/media/media-jvm
engine-js / yt-media                    core/service/kotlinYtmusicScraper
remote-config                           composeApp updater & core/service
(none)                                  core/service/autoEqService (AutoEq)
(none)                                  core/service/lyricsService (LRCLIB/BetterLyrics)
(none)                                  core/service/spotify (Spotify Canvas)
(none)                                  core/service/discord (Discord RPC)
(none)                                  core/service/listenTogether (Wire-Compatible Rooms)
```

---

## 4. Legal Attribution & License Preservation

PRVN Music is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
All copyright headers, upstream author notices, and source attributions are preserved:
- **SimpMusic:** Copyright (c) 2024-2026 maxrave-dev and contributors.
- **YMusic V4:** Copyright (c) ymusic-io and contributors.
- **NewPipe / PipePipe Extractors:** Copyright (c) TeamNewPipe and PipePipe contributors.
- **AutoEq:** Copyright (c) Jaakko Pasanen.
