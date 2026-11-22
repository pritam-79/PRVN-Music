# PRVN Music v0.14 — Logo Extraction Report

**Date:** November 22, 2026  
**Pipeline:** Automated Sub-Pixel Crop & Bicubic Synthesis  
**Output Directory:** `assets/branding/logos/`  
**Density Outputs:** `androidApp/src/main/res/mipmap-*` & `composeApp/src/commonMain/composeResources/drawable/`  

---

## 1. Extraction Methodology

Extraction was executed using a specialized PowerShell `.NET System.Drawing` script with bicubic re-sampling and sub-pixel edge alignment.

### Sheet 1 Profiling ($1024 \times 682\text{ px}$)
- **Column Geometry:** Four columns centered at $X = 144, 400, 656, 912$.
- **Row Geometry:**
  - Row 0: Top squircle boundary $Y = 12$, Bottom boundary $Y = 188$ ($H = 176\text{px}$). Captions at $Y = 222$ strictly excluded.
  - Row 1: Top squircle boundary $Y = 240$, Bottom boundary $Y = 416$ ($H = 176\text{px}$). Captions at $Y = 428$ strictly excluded.
  - Row 2: Top squircle boundary $Y = 449$, Bottom boundary $Y = 629$ ($H = 180\text{px}$). Captions at $Y = 650$ strictly excluded.

### Sheet 2 Profiling ($1024 \times 682\text{ px}$)
- **Column Geometry:** Three columns centered at $X = 184, 511, 839$.
- **Row Geometry:**
  - Row 0: Top squircle boundary $Y = 35$, Bottom boundary $Y = 287$ ($H = 252\text{px}$). Captions at $Y = 304$ strictly excluded.
  - Row 1: Top squircle boundary $Y = 346$, Bottom boundary $Y = 600$ ($H = 254\text{px}$). Captions at $Y = 622$ strictly excluded.

---

## 2. Asset Integrity & Dimension Audit

For every one of the 17 logos, the pipeline produced:
1. `master.png`: Raw uncompressed master crop.
2. `logo_1024.png`: High-resolution $1024 \times 1024\text{ px}$ master asset with HighQualityBicubic interpolation.
3. `logo_512.png`: $512 \times 512\text{ px}$ asset for store listings and desktop distribution.
4. `thumb_96.png`: $96 \times 96\text{ px}$ crisp thumbnail for in-app grid rendering.
5. `ic_launcher_prvn_logo_XX.png`: Square adaptive icons across 5 Android mipmap densities (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`).
6. `ic_launcher_round_prvn_logo_XX.png`: Round circular masked icons across all 5 mipmap densities.

### Per-Logo Extraction Verification

| Internal ID | Name | Raw Dimensions | Output 1024 | Output 512 | Output 96 | Status |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| `prvn_logo_01` | Aurora Play | $176 \times 176$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_02` | Headphone Pulse | $176 \times 176$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_03` | Waveform Circle | $176 \times 176$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_04` | Vinyl Player | $176 \times 176$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_05` | Minimal P | $176 \times 176$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_06` | Equalizer | $176 \times 176$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_07` | Infinity Sound | $176 \times 176$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_08` | Neon Ring | $176 \times 176$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_09` | Glassmorphism | $176 \times 180$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_10` | Liquid Glass (Special) | $176 \times 180$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_11` | Cosmic | $176 \times 180$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_12` | Premium Metal | $176 \times 180$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_13` | Minimal Neon (Default) | $254 \times 252$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_14` | Audio Wave | $254 \times 252$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_15` | Vinyl Classic | $254 \times 252$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_16` | Play & Wave | $254 \times 254$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |
| `prvn_logo_17` | Premium Crown | $254 \times 254$ | $1024 \times 1024$ | $512 \times 512$ | $96 \times 96$ | PASS |

---

## 3. Boundary Edge Purity Validation

Every logo boundary was verified to ensure zero text bleed from adjacent captions:
- Top border brightness: verified clean squircle edge highlight with darkness above.
- Bottom border brightness: verified that captions located at $Y=222, 428, 650$ (Sheet 1) and $Y=304, 622$ (Sheet 2) are not present in any cropped asset.
- Left and right gutters: verified pure black margin transitions.
