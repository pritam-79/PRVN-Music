# PRVN Music v0.14 — Software Bill of Materials & Dependencies (SBOM)

**Release:** PRVN Music v0.14.0  
**Target SDK:** 35 / 36  
**Min SDK:** 26 (Android 8.0 Oreo)  

---

## Direct Multiplatform & Android Dependencies

### Core & Framework
| Dependency | Version | Purpose | License |
|---|---|---|---|
| `org.jetbrains.compose` | 1.12.2 | Compose Multiplatform framework | Apache 2.0 |
| `androidx.compose.material3:material3` | 1.5.0-alpha22 | Material Design 3 Expressive UI | Apache 2.0 |
| `androidx.media3:media3-exoplayer` | 1.11.1 | Android audio playback engine | Apache 2.0 |
| `androidx.media3:media3-session` | 1.11.1 | Background MediaSession & Notifications | Apache 2.0 |
| `androidx.room:room-runtime` | 2.8.5 | Local persistent database | Apache 2.0 |
| `androidx.datastore:datastore-preferences` | 1.3.0 | Key-value settings storage | Apache 2.0 |
| `io.insert-koin:koin-core` | 4.2.2 | Dependency injection | Apache 2.0 |
| `io.ktor:ktor-client-core` | 3.6.0 | Asynchronous HTTP client | Apache 2.0 |
| `io.github.dokar3:quickjs-kt` | 1.0.15 | Embedded QuickJS engine for YouTube player logic | Apache 2.0 |

### Audio & Lyrics
| Dependency | Version | Purpose | License |
|---|---|---|---|
| `AutoEq Headphone Index` | v1.0.0 | Frequency response correction curves | MIT |
| `com.github.TeamNewPipe:nanojson` | c7a6c1c | Fast JSON parser for stream extractors | Apache 2.0 |
| `com.atilika.kuromoji:kuromoji-ipadic` | 0.9.0 | Japanese lyrics romanization | Apache 2.0 |
| `com.belerweb:pinyin4j` | 2.5.1 | Chinese lyrics pinyin romanization | GPL 2.0 / LGPL |
| `dev.chrisbanes.haze:haze` | 2.0.1 | Frosted glass backdrop blur | Apache 2.0 |
| `com.github.kyant0:liquid-glass` | 2.0.1 | Liquid Glass UI visual elements | MIT |

### Desktop Platform
| Dependency | Version | Purpose | License |
|---|---|---|---|
| `net.java.dev.jna:jna` | 5.19.1 | Java Native Access for libmpv | Apache 2.0 / LGPL |
| `libmpv` | C-API | Desktop audio & video decoding engine | GPL 2.0+ / LGPL 2.1+ |
| `Conveyor` | 2.0 | Desktop native package distribution | Commercial / Open |

---

## Security Audit Summary for Google Play Protect

1. **No Untyped Dynamic Code Loading:** All native binaries (`libmpv`, `quickjs`) are compiled and packaged at build time. No arbitrary DEX or APK loading is permitted.
2. **Minimal Permissions:** Scoped strictly to media playback, background audio, and optional local storage.
3. **No Hidden Telemetry:** FOSS compilation path strips proprietary analytics.
