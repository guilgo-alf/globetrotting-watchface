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

All eight sessions shipped end-to-end:

- **Session 1** ✅ — minimal time-only face. Black background, `Hmm` time, seconds superscript.
- **Session 2** ✅ — status line + date. Timezone grid stripped at user request for a cleaner symmetrical look (data structure kept in `TimezoneConfig.kt` for future re-add).
- **Session 3** ✅ — real stats providers (`BatteryProvider`, `StepsProvider`) plus invisible complication slots for weather + phone battery. AOD already covered by per-layout `isAmbient` checks.
- **Session 4** ✅ — `CompassSensorManager` (`TYPE_ROTATION_VECTOR`), `BearingSmoother` (α=0.05 low-pass), `CompassOverlayLayout` initially a 6 px amber dot, later refactored to a stroked red arc rotated around canvas centre.
- **Session 5** ✅ — `ExtrasTileService` with rich timezone band visualisation, DST-aware abbreviations, deep-work dot rule (black 09:00–16:30, white otherwise), home-minute echo collapse.
- **Session 6** ✅ — `ZonesActivity` companion screen — Wear Compose `ScalingLazyColumn` with rotary bezel input. **Removed in §11d** — see below.
- **Session 7** ✅ — pre-ship hardening pass. See §11c.
- **Session 8** ✅ — first-watch sideload + handoff prep. See §11d.

## 11a — Spec deviations from the original brief

Recorded so the next session can pick up cleanly:

1. **Status line content**: brief said `{steps} · {tempC}°C · {hr} · {batteryPct}`. After §11c, the watch face status line is **`{steps} · {watchBatteryPct}%`** — phone battery and weather were dropped because they have no real on-device data source. Restoring them needs DataLayer (phone battery via paired-phone messaging) or a complication source for weather.
2. **Timezone grid**: stripped to date-only for symmetry (status / time / date as three centred elements). `DEFAULT_ZONES` and `GRID_CELLS` kept in `watchface/data/TimezoneConfig.kt`, just not referenced. Re-introducing the grid is a one-line change to `GRID_CELLS`.
3. **Layout tokens**: ended at 160 px big time, -13 letter tracking, 221 px centre Y. OpenDyslexic is wider than the Facer original's font, so brief coords needed compression.
4. **Tile content**: 8 saved zones, west-to-east. Tap opens `ZonesActivity` for the full ~36-zone scrollable list.

## 11c — Pre-ship hardening pass (Session 7)

Trail of decisions made before sideloading to real Galaxy Watch 6 Classic:

1. **Dropped fake fields from status line** — `phoneBatteryForPreview = 20` and `tempForPreview = 22` were stubs that would lie permanently on real hardware. New format: `{steps} · {watchBatteryPct}%`.
2. **CET/CEST flag stays 🇪🇺 always** — `resolveCetFlag()` was dead code (defined, never called); deleted along with `cityFromZone()` and the `cityOverride` field on `SavedZone`.
3. **Tile cache busts on `ACTION_TIMEZONE_CHANGED`** — `ExtrasTileService` registers a `BroadcastReceiver` (with `ACTION_LOCALE_CHANGED` too) so the highlighted local row follows when the user travels. Replaces the previous "wait 60 s for freshness" fallback.
4. **`ZonesActivity` rotary bezel wired up** — `Modifier.rotaryScrollable` + `FocusRequester` requested in a `LaunchedEffect`. Without this the bezel rotated but no scroll happened.
5. **Wear-specific theme** — created `res/values/themes.xml` with `WearActivityTheme`; activity declares `WindowCompat.setDecorFitsSystemWindows(window, false)` for proper round-screen edge-to-edge.
6. **`ACTIVITY_RECOGNITION` runtime grant via install script** — both `install.ps1` and `install.sh` run `adb shell pm grant com.guil.globetrotting android.permission.ACTIVITY_RECOGNITION` after install. Without this, `StepsProvider` silently returns 0 on Android 10+.
7. **Sensor lifecycle hardening** — `CompassSensorManager.stop()` nulls the listener before unregistering; `onSensorChanged` snapshots locally for atomic null check. `GlobetrottingRenderer` adds a `destroyed` flag so any in-flight sensor callback after destroy returns early.
8. **De-duplicated `formatOffset`** — moved to top-level in `TimezoneTileConfig.kt`, consumed by both `ExtrasTileRenderer` and `ZonesActivity`.
9. **Linked the band corner-fill kludge to its source colour** — `PILL_TINT_ARGB` and `PILL_TINT_OVER_BLACK_RGB` are now paired constants with a comment linking them. RGB_565 has no alpha so the band corners can't be transparent; this is the cleanest way to keep the two values from drifting silently.
10. **ProGuard rules pre-loaded** — minify is still off in release, but `proguard-rules.pro` now contains the full keep set for watch-face binders, tile binders, Compose, Wear Compose, and our entry-point services. Trapdoor for if minify ever flips on.
11. **`BatteryProvider` Integer.MIN_VALUE sentinel** — explicit early return on the unsupported-property case rather than relying on `coerceIn` clamping to 0.
12. **`ZonesActivity` column widths fixed for OpenDyslexic** — code column bumped 34dp → 50dp with `maxLines=1` so 4-letter codes (AKDT, NZDT, AEDT) don't soft-wrap onto two lines.
13. **Wear Compose 1.4.0** — left at 1.4.0; the `rotaryScrollable` API and `RotaryScrollableDefaults` are stable in this version.

## 11d — First-watch sideload + handoff prep (Session 8)

First successful sideload to a real Galaxy Watch 6 Classic. Several issues only visible on real hardware:

1. **Watch face binding hung with `NoOpCanvasComplication`** — our placeholder
   complication implementation didn't advance the slot's data state machine,
   so `createWatchFace` timed out after 10 s on real Wear OS 5 (error code 4).
   Emulator was lenient; only real-watch enforcement caught it. Fixed by
   replacing the NoOp with `CanvasComplicationDrawable` configured with all
   colours `Color.TRANSPARENT`. Adds `watchface-complications-rendering:1.2.1`.
2. **`ZonesActivity` removed entirely** — after the trim to the same 8 saved
   zones as the tile, the activity added zero unique value. Removed: file,
   manifest entry, `themes.xml`, all Compose-for-Wear deps, `kotlin("plugin.compose")`.
   APK shrank by ~12 MB. Tile is no longer Clickable.
3. **Compass disabled on real watch** — Galaxy Watch 6 Classic's metallic
   rotating bezel sits millimetres from the magnetometer and produces wildly
   unreliable readings without manual figure-8 calibration. Re-enabling needs
   a calibration UX (deferred — see HANDOFF.md task 4).
4. **Step counter unreliable** — Samsung's `TYPE_STEP_COUNTER` firmware
   batches step events aggressively; can go 30+ minutes without firing. The
   "right" fix is `androidx.health:health-services-client` with
   `DataType.STEPS_DAILY` (deferred — see HANDOFF.md task 2).
5. **Steps persistence** — `StepsProvider` now persists `(date, dayStartCounter)`
   to SharedPreferences so face-switches don't reset today's count.
6. **Customize flow not exposed in Samsung One UI Watch** — the system editor
   doesn't appear via long-press on the Galaxy Watch 6 Classic; needs a
   custom editor activity (deferred — see HANDOFF.md task 1).
7. **Status-line restored to four fields** — re-added the weather + phone
   battery slots with placeholders that fall back to the complication value
   when slots are filled. Watch face shows `steps · tempC · watch · phone`,
   no `%` suffix on battery values to match the Facer reference. AOD now
   shows the status line at FAINT colour.
8. **Watch face metrics +2 px** across all fonts; date label moved 1 px down.
   Compass arc moved 6 px outward (radius 222) per real-watch feedback.
9. **Screenshot / preview mode** — `GlobetrottingRenderer.isPreviewEmulator`
   detects the Android Studio AVD and shows compass arc statically at 0° +
   step count `17,456` so screenshots show feature-complete state regardless
   of sensor availability. Same APK runs unchanged on real hardware.
10. **Renamed face to "Globetrotter v2"** during sideload debugging to
    disambiguate from cached prior installs. Plus `Globetrotter Timezones` on
    the tile.
11. **`HANDOFF.md` written** to brief a Wear OS specialist on the four
    remaining issues (Customize editor, Health Services for steps, real
    preview thumbnail, optional compass calibration UX).

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
