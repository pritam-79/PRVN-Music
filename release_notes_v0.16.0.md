# PRVN Music v0.16 (Build 20) — Release Notes

## Summary
PRVN Music v0.16 (Build 20) is a major release dedicated to completely repairing Android launcher icon switching, implementing full adaptive icon safe-zone compliance, preserving the complete 18-logo official reference collection from Concept Sheets 1 & 2, eliminating inherited legacy YMusic branding from repository assets, and ensuring rock-solid launcher icon persistence across reboots, app updates, and OEM launchers.

---

## Key Highlights & Architectural Changes

### 1. Real Android Launcher Icon Switching
* **Native Activity-Alias Architecture:** Replaced fragile and conflicting launcher configurations with 18 distinct `<activity-alias>` declarations in `AndroidManifest.xml`, each targeting `MainActivity` with `${applicationId}.MainActivityAliasLogoXX`.
* **Safe State Machine in PackageManager:**
  * Enables target activity alias *first*, guaranteeing the application icon never disappears from the home screen or app drawer.
  * Disables previously enabled aliases cleanly without broadcast flooding (`DONT_KILL_APP`).
  * Preserves `MainActivity` in `COMPONENT_ENABLED_STATE_DEFAULT`, ensuring system media sessions, deep links (`simpmusic://`, `prvnmusic://`, `vnd.youtube`), car templates, and widgets remain operational.
* **Universal OEM Launcher Compatibility:** Tested and verified across Samsung OneUI, Xiaomi MIUI/HyperOS, OnePlus/Oppo/Realme OxygenOS/ColorOS, Google Pixel Launcher, Motorola, POCO, Vivo, and Nothing OS.
* **Persistent Settings Continuity:** Selected icons are saved in DataStore and synchronized on application startup via `SimpMusicApplication`.

### 2. Complete 18 Official Reference Logos Collection
Full preservation of all 18 logos from Concept Sheets 1 and 2 (located in `PRVN_Music_All_Logos_and_Sheets`):

#### Sheet 1 — Original Concepts (6 Logos)
1. **V1 — Minimal Neon** (`prvn_logo_01`) — **OFFICIAL PRIMARY DEFAULT**
2. **V2 — Audio Wave** (`prvn_logo_02`)
3. **V3 — Vinyl Classic** (`prvn_logo_03`)
4. **V4 — Play & Wave** (`prvn_logo_04`)
5. **V5 — Premium Crown** (`prvn_logo_05`)
6. **V6 — Liquid Glass (Special)** (`prvn_logo_06`) — **SPECIAL EDITION**

#### Sheet 2 — Recent Concepts (12 Logos)
7. **V1 — Aurora Play** (`prvn_logo_07`)
8. **V2 — Headphone Pulse** (`prvn_logo_08`)
9. **V3 — Waveform Circle** (`prvn_logo_09`)
10. **V4 — Vinyl Player** (`prvn_logo_10`)
11. **V5 — Minimal P** (`prvn_logo_11`)
12. **V6 — Equalizer** (`prvn_logo_12`)
13. **V7 — Infinity Sound** (`prvn_logo_13`)
14. **V8 — Neon Ring** (`prvn_logo_14`)
15. **V9 — Glassmorphism** (`prvn_logo_15`)
16. **V10 — Liquid Glass (Special)** (`prvn_logo_16`) — **SPECIAL EDITION**
17. **V11 — Cosmic** (`prvn_logo_17`)
18. **V12 — Premium Metal** (`prvn_logo_18`)

### 3. Android Adaptive Icon Safe-Zone Compliance
* **Zero Clipping:** Foreground art is centered and scaled to ~65% of the 108dp canvas, ensuring the glowing shapes and `PRVN MUSIC` lettering reside completely within the **66dp safe zone**.
* **Mask Adaptability:** Tested against circular (Pixel), squircle (OneUI/MIUI), teardrop, and rounded-square system masks.
* **Multi-Density Generation:** High-quality bicubic mipmaps generated for `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`, and API 26+ `mipmap-anydpi-v26`.

### 4. Project Branding & Asset Cleansing
* Completely purged legacy YMusic vector drawables (`ic_launcher_dj`, `ic_launcher_minimal`, `ic_launcher_neon`, `ic_launcher_original`, `ic_launcher_purple`) from resource directories.
* Updated `README.md` and repository graphics to showcase the official **PRVN Music Minimal Neon** brandmark.
* Preserved all open-source licensing, upstream SimpMusic attribution, and GPL-3.0 compliance.

### 5. In-App Updater Continuity
* Seamless detection of `versionCode: 20` (`versionName: "0.16"`).
* Direct in-app background download, byte counters, speed meters, SHA-256 verification, and package installer flow.

---

## Release Binaries

| Asset | Architecture | Status |
| :--- | :--- | :--- |
| `PRVN-Music-v0.16-arm64.apk` | `arm64-v8a` | Release Build |
| `PRVN-Music-v0.16-universal.apk` | Universal | Release Build |
| `CHECKSUMS.txt` | Hashes | SHA-256 |
