# Globetrotting Minimalist — Wear OS Watch Face Project

> Single-document brief for Claude Code. Hand this whole file to Claude Code as `CLAUDE.md` in a fresh project directory and say "read this and execute".

---

## 0 — Scope and tone

Personal Wear OS watch face for **Galaxy Watch 6 Classic 47mm** (480×480 circular, Wear OS 5+). Sideload-only, no Play Store.

**Tone for Claude Code**: efficient and direct. The user is technical but not learning Android. Just hand them a working APK at the end of each session. Comment code where genuinely non-obvious; otherwise let it speak. Default to "ship it" rather than "explain it".

**Goal of this project**:
1. Recreate this Facer face natively in Kotlin: `https://beta.facer.io/watchface/93AVu164TV?watchModel=galaxywatch6classicsilver`
2. Add a magnetometer-driven compass marker on the bezel
3. Add a swipe-accessible Tile with bonus info

Reference design — actual photo of the original from the user's wrist:

```
       ┌─────────────────────────────────────┐
       │       1972 · 29°C · 32 · 65         │   ← status line
       │                                     │
       │                                     │
       │           1 6 2 6  ¹⁷               │   ← big time + seconds
       │                                     │
       │                                     │
       │   7 PDT // -9  8 PVT // -8  10 EDT // -6 │
       │              11 BRT // -5    SAT 02      │   ← 3-column
       │              16 CEST // 2                │      timezone +
       │           1956 IST // 3.5                │      date grid
       │             22 CST // 6                  │
       └─────────────────────────────────────┘
```

The timezone block is a **three-column grid**, not a single column. Date `SAT 02` lives in the bottom-right cell of that grid.

---

## 1 — Tech stack (pinned)

- **Language**: Kotlin only.
- **Build**: Gradle with Kotlin DSL (`build.gradle.kts`).
- **Watch face**: `androidx.wear.watchface:watchface:1.2.+` using `Renderer.CanvasRenderer2`. NOT Watch Face Format — we need raw sensor access for the compass.
- **Tiles**: `androidx.wear.protolayout:protolayout:1.2.+` and `androidx.wear.protolayout:protolayout-material:1.2.+`.
- **Date/time**: `java.time` (`ZonedDateTime`, `ZoneId`). No legacy `Calendar`/`Date`.
- **Sensors**: `SensorManager` with `Sensor.TYPE_ROTATION_VECTOR` for compass (fused, much cleaner than raw mag/accel).
- **Min SDK**: 33 (Wear OS 4 baseline).
- **Target SDK**: latest stable.
- **No external libraries beyond AndroidX.** If something seems to need a library, flag it before adding.

---

## 2 — Project structure (target)

```
/
├── CLAUDE.md                     ← this file
├── README.md                     ← human summary
├── bootstrap.ps1                 ← one-shot Windows setup + build
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── docs/
│   └── diagnose.md               ← Windows build troubleshooting
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/guil/globetrotting/
│       │   ├── watchface/
│       │   │   ├── GlobetrottingWatchFaceService.kt
│       │   │   ├── GlobetrottingRenderer.kt
│       │   │   ├── RenderState.kt
│       │   │   ├── layout/
│       │   │   │   ├── BigTimeLayout.kt
│       │   │   │   ├── StatusLineLayout.kt
│       │   │   │   ├── TimezoneGridLayout.kt   ← NB: grid not stack
│       │   │   │   └── CompassOverlayLayout.kt
│       │   │   └── data/
│       │   │       ├── TimezoneConfig.kt
│       │   │       └── WatchFaceColors.kt
│       │   ├── compass/
│       │   │   ├── CompassSensorManager.kt
│       │   │   └── BearingSmoother.kt
│       │   ├── stats/
│       │   │   ├── BatteryProvider.kt
│       │   │   ├── StepsProvider.kt
│       │   │   └── HeartRateProvider.kt
│       │   └── tile/
│       │       ├── ExtrasTileService.kt
│       │       ├── ExtrasTileRenderer.kt
│       │       └── TileExtrasConfig.kt
│       └── res/
│           ├── font/
│           │   └── opendyslexic_regular.otf
│           ├── values/
│           │   ├── strings.xml
│           │   ├── themes.xml
│           │   └── watch_face_metadata.xml
│           └── xml/
│               └── watch_face.xml
└── scripts/
    ├── install.sh         install.ps1
    └── make-keystore.sh   make-keystore.ps1
```

---

## 3 — Working agreement

- **Each work session ends with a buildable APK.** If we can't ship at session end, that's a failure mode — propose smaller scope.
- **Build the watch face first, end-to-end, even if minimal.** Don't go deep on compass before the time displays work. Vertical slices, not features.
- **Match the photo for visual design**, but use proper Kotlin/Android idioms internally. Don't port Facer's hacks — Facer's DST ternary opacity layering is unnecessary because `ZonedDateTime` handles DST natively.
- **The user iterates by looking at the watch.** Push frequently; don't batch up many design changes.
- **No publishing concerns.** Sideload-only. Skip Play Store metadata, screenshots, listings.
- **Generate a debug keystore once**, reuse for all builds.
- **Versioning**: auto-increment `versionCode` from build timestamp to avoid manual bumching.

---

## 11 — Session goals — STATUS

All five sessions shipped end-to-end:

- **Session 1** ✅ — minimal time-only face. Black background, `Hmm` time, seconds superscript.
- **Session 2** ✅ — status line + date. Timezone grid stripped at user request for a cleaner symmetrical look (data structure kept in `TimezoneConfig.kt` for future re-add).
- **Session 3** ✅ — real stats providers (`BatteryProvider`, `StepsProvider`) plus invisible complication slots for weather + phone battery. AOD already covered by per-layout `isAmbient` checks.
- **Session 4** ✅ — `CompassSensorManager` (`TYPE_ROTATION_VECTOR`), `BearingSmoother` (α=0.15 low-pass), `CompassOverlayLayout` (6 px amber dot at radius 220). Lifecycle: starts when visible+interactive, stops on ambient/hidden/destroy.
- **Session 5** ✅ — `ExtrasTileService` with three bonus zones (Tokyo / Mexico / London), steps-vs-goal, watch + phone battery, long-form date. Built with `androidx.wear.protolayout` 1.2.1, refresh interval 60 s.

## 11a — Spec deviations from the original brief

Recorded so the next session can pick up cleanly:

1. **Status line content**: brief said `{steps} · {tempC}°C · {hr} · {batteryPct}` — user dropped HR, requested watch+phone battery instead. Final format: `{steps} · {tempC}°C · {watchBatteryPct} · {phoneBatteryPct}`. `HeartRateProvider` was never written.
2. **Timezone grid**: stripped to date-only for symmetry (status / time / date as three centred elements). `DEFAULT_ZONES` and `GRID_CELLS` kept in code, just not referenced. Re-introducing the grid is a one-line change to `GRID_CELLS`.
3. **Layout tokens**: ended at 130 px big time (was 130 in brief, briefly 180), -10 letter tracking (was -2), 215 px centre Y (was 235). OpenDyslexic is wider than the Facer original's font, so brief coords needed compression.
4. **Tile content**: brief had HR; we serve watch+phone battery instead, mirroring the face status line.

## 11b — Known environmental gotcha: Wear OS 5 emulator

The Google Wear OS 5 emulator's picker (`WFInfoResolver`) actively rejects "legacy" AndroidX watch faces — only WFF (Watch Face Format declarative XML) faces appear in its long-press carousel. Logcat fingerprint:

```
W/WearServices: [WFInfoResolver]Unsupported legacy watch face
WatchFaceId[com.guil.globetrotting,...GlobetrottingWatchFaceService].
```

Workaround: develop against a **Wear OS 4 (API 33) AVD** — its picker accepts legacy faces. The real Galaxy Watch 6 Classic uses Samsung's One UI Watch picker, which also accepts legacy faces regardless of Wear OS version. Wear OS 6 (Baklava, API 36) may extend this restriction to real devices; if/when that bites, options are: (a) bump `androidx.wear.watchface` past 1.3 alpha, or (b) port to WFF (loses raw sensor access, breaks the compass).

See `docs/diagnose.md` for the AF_UNIX `tmpdir` workaround that may also be needed in unusual Windows environments.

---

## 12 — Things deliberately decided

A few opinionated choices baked in:

- **Black background, white text** — matches Facer original
- **Three-column grid for timezones**, with date as a grid cell (not floating element)
- **OpenDyslexic-Regular** as single font, sized by component
- **Compass via rotation vector**, not raw magnetometer
- **No complications**, no user customisation — this is your watch face, not a publishable one
- **Tile contents are different from watch face** (Tokyo/Mexico/London) — complementary info
- **Static abbreviations** (CEST stays CEST in winter) — stylistic choice
- **No animations, no fade-ins** — pure minimalism
- **AOD shows full timezone grid** at `Faint` colour — user wants info density even in AOD
- **Auto-incrementing versionCode** from timestamp — no manual bumping

---

## 13 — Credits

OpenDyslexic font under SIL Open Font License (redistributable).
