# PRVN Music v0.14 — Logo Source Mapping

**Date:** November 22, 2026  
**Application ID:** `com.prvn.music`  
**Version:** 0.14.0 (Version Code: 18)  
**Primary Default Application Logo:** `prvn_logo_13` (V1 — Minimal Neon)  

---

## 1. Reference Sheets Overview

All logos are extracted directly from the two official user-uploaded reference sheets without artificial regeneration or redesign:
- **Reference Sheet 1 (`media_1795359391738_ee5acfcb.jpg`):** $1024 \times 682\text{ px}$, 12 designs across 3 rows $\times$ 4 columns.
- **Reference Sheet 2 (`media_1795359391980_2d1ab03a.jpg`):** $1024 \times 682\text{ px}$, 6 designs across 2 rows $\times$ 3 columns.
- **Deduplication:** V6 *Liquid Glass (Special)* appears in both sheets; per design requirements, it is represented once in the collection as `prvn_logo_10` (*V10 — Liquid Glass (Special)*), yielding exactly **17 distinct official designs**.

---

## 2. Complete 17-Logo Source Mapping Table

| ID | Display Name | Sheet | Row | Col | Crop Box $(x, y, w, h)$ | Launcher Component / Alias | Role |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- | :--- |
| `prvn_logo_13` | **V1 — Minimal Neon** | 2 | 0 | 0 | `(57, 35, 254, 252)` | `com.maxrave.simpmusic.MainActivity` | **PRIMARY DEFAULT** |
| `prvn_logo_14` | **V2 — Audio Wave** | 2 | 0 | 1 | `(384, 35, 254, 252)` | `MainActivityAliasLogo14` | Alternate |
| `prvn_logo_15` | **V3 — Vinyl Classic** | 2 | 0 | 2 | `(712, 35, 254, 252)` | `MainActivityAliasLogo15` | Alternate |
| `prvn_logo_16` | **V4 — Play & Wave** | 2 | 1 | 0 | `(57, 346, 254, 254)` | `MainActivityAliasLogo16` | Alternate |
| `prvn_logo_17` | **V5 — Premium Crown** | 2 | 1 | 1 | `(384, 346, 254, 254)` | `MainActivityAliasLogo17` | Alternate |
| `prvn_logo_01` | **V1 — Aurora Play** | 1 | 0 | 0 | `(56, 12, 176, 176)` | `MainActivityAliasLogo01` | Alternate |
| `prvn_logo_02` | **V2 — Headphone Pulse** | 1 | 0 | 1 | `(312, 12, 176, 176)` | `MainActivityAliasLogo02` | Alternate |
| `prvn_logo_03` | **V3 — Waveform Circle** | 1 | 0 | 2 | `(568, 12, 176, 176)` | `MainActivityAliasLogo03` | Alternate |
| `prvn_logo_04` | **V4 — Vinyl Player** | 1 | 0 | 3 | `(824, 12, 176, 176)` | `MainActivityAliasLogo04` | Alternate |
| `prvn_logo_05` | **V5 — Minimal P** | 1 | 1 | 0 | `(56, 240, 176, 176)` | `MainActivityAliasLogo05` | Alternate |
| `prvn_logo_06` | **V6 — Equalizer** | 1 | 1 | 1 | `(312, 240, 176, 176)` | `MainActivityAliasLogo06` | Alternate |
| `prvn_logo_07` | **V7 — Infinity Sound** | 1 | 1 | 2 | `(568, 240, 176, 176)` | `MainActivityAliasLogo07` | Alternate |
| `prvn_logo_08` | **V8 — Neon Ring** | 1 | 1 | 3 | `(824, 240, 176, 176)` | `MainActivityAliasLogo08` | Alternate |
| `prvn_logo_09` | **V9 — Glassmorphism** | 1 | 2 | 0 | `(56, 449, 176, 180)` | `MainActivityAliasLogo09` | Alternate |
| `prvn_logo_10` | **V10 — Liquid Glass (Special)** | 1 | 2 | 1 | `(312, 449, 176, 180)` | `MainActivityAliasLogo10` | **SPECIAL EDITION** |
| `prvn_logo_11` | **V11 — Cosmic** | 1 | 2 | 2 | `(568, 449, 176, 180)` | `MainActivityAliasLogo11` | Alternate |
| `prvn_logo_12` | **V12 — Premium Metal** | 1 | 2 | 3 | `(824, 449, 176, 180)` | `MainActivityAliasLogo12` | Alternate |

---

## 3. Platform Distribution Mapping

1. **Android Default Launcher Icon (`ic_launcher.png`, `ic_launcher_round.png`):**
   - Sourced from `prvn_logo_13` (Minimal Neon).
   - Deployed across `mipmap-mdpi` (48px), `mipmap-hdpi` (72px), `mipmap-xhdpi` (96px), `mipmap-xxhdpi` (144px), `mipmap-xxxhdpi` (192px).
2. **Desktop Application Icon (`desktopApp/src/jvmMain/resources/icon.png`):**
   - High-resolution $512 \times 512\text{ px}$ derivative of `prvn_logo_13`.
3. **Dynamic Launcher Switching:**
   - 16 `<activity-alias>` declarations target `com.maxrave.simpmusic.MainActivity`.
   - Dynamic activation via Android `PackageManager.setComponentEnabledSetting()`.
4. **In-App Compose App Icon Selector:**
   - Available under **Settings $\rightarrow$ User Interface $\rightarrow$ App Icon**.
   - Displays all 17 designs in a 2/3-column responsive grid with live previews, default & special badges, and reset capability.
