# Globetrotting Minimalist

Personal Wear OS watch face + tile for the Galaxy Watch 6 Classic 47mm.
Native Kotlin, AndroidX `Renderer.CanvasRenderer2`, sideload-only — not on Play Store.

Reverse-engineered from a Facer face the owner liked, then iteratively polished on a real wrist over many sessions. Single-font OpenDyslexic typography, AMOLED-tuned palette, deep-work-aware band visualisations on the timezone tile.

## What's on the screen

**Watch face** (interactive + AOD):
- Big 24-hour digits, no leading zero, no colon (`Hmm`), tiny seconds superscript
- Status line: `steps · tempC · watchBattery · phoneBattery` (no `%` suffix — matches Facer reference)
- `EEE dd` date (`SUN 03`) centred below the time
- Red arc on the bezel marking magnetic north (currently disabled on real hardware — see Open issues)

**Tile** (swipe right from face):
- 8 saved zones, west-to-east, each with code · flag · time · 24h band · offset relative to home
- Local zone gets a subtle pill highlight; same-minute-as-home rows collapse `:mm` to invisible black so the eye lands on the meaningful hour
- Band visualisation: warm-amber working hours (9-17), now-dot turns black between 09:00–16:30 (deep-work) and white otherwise
- DST-aware abbreviations via `ZoneId.rules.isDaylightSavings`

## Project layout

```
app/src/main/kotlin/com/guil/globetrotting/
├── watchface/
│   ├── GlobetrottingWatchFaceService.kt   ← service + complication slots wiring
│   ├── GlobetrottingRenderer.kt           ← Canvas render loop, sensor lifecycle
│   ├── ComplicationSlots.kt               ← 2 invisible slots (weather + phone battery)
│   ├── RenderState.kt                     ← per-frame data the layouts consume
│   ├── layout/
│   │   ├── BigTimeLayout.kt               ← Hmm digits + seconds superscript
│   │   ├── StatusLineLayout.kt            ← top-row status text (interactive + AOD)
│   │   ├── TimezoneGridLayout.kt          ← currently date-only; full grid retained for future
│   │   └── CompassOverlayLayout.kt        ← red arc, rotated by canvas around centre
│   └── data/
│       ├── WatchFaceColors.kt             ← palette + metric constants
│       └── TimezoneConfig.kt              ← grid cells (date-only) + dead grid data
├── compass/
│   ├── CompassSensorManager.kt            ← TYPE_ROTATION_VECTOR registration + event dispatch
│   └── BearingSmoother.kt                 ← low-pass filter with 0/360 wrap handling
├── stats/
│   ├── BatteryProvider.kt                 ← BatteryManager.BATTERY_PROPERTY_CAPACITY
│   └── StepsProvider.kt                   ← TYPE_STEP_COUNTER + SharedPreferences persistence
└── tile/
    ├── ExtrasTileService.kt               ← TileService, TIMEZONE_CHANGED receiver, view model
    ├── ExtrasTileRenderer.kt              ← ProtoLayout layout + RGB_565 band bitmap
    └── TimezoneTileConfig.kt              ← SAVED_ZONES (8) + flag map + formatOffset
```

Architectural decisions and session-by-session history live in [`CLAUDE.md`](CLAUDE.md). Windows-specific build troubleshooting in [`docs/diagnose.md`](docs/diagnose.md).

## Stack

- Kotlin 2.0.21, Gradle Kotlin DSL
- `androidx.wear.watchface:1.2.1` — `Renderer.CanvasRenderer2` (NOT Watch Face Format — we need raw sensor access for the compass)
- `androidx.wear.tiles:1.4.1` + `androidx.wear.protolayout:1.2.1`
- `androidx.wear.watchface:watchface-complications-rendering:1.2.1` (for `CanvasComplicationDrawable`)
- `java.time` for dates / timezones (DST handled natively, no `Calendar`)
- Min SDK 33 (Wear OS 4 baseline), target SDK 35
- Single font: OpenDyslexic-Regular (SIL OFL, redistributable)
- No external libraries beyond AndroidX

## Build & install

One-time setup:

```bash
./scripts/make-keystore.sh    # creates ~/.android/globetrotting-debug.keystore
```

(Or `make-keystore.ps1` on Windows.)

Per-session loop:

```bash
./gradlew assembleRelease && ./scripts/install.sh
```

The install script grants `ACTIVITY_RECOGNITION` after install (required for the step counter on Android 10+; without it `StepsProvider` silently returns 0 forever).

APK lands at `app/build/outputs/apk/release/app-release.apk`. After first install: long-press the active face → scroll the picker → tap **Globetrotter v2**.

### Connecting to the watch

Use the phone's hotspot, not home Wi-Fi (most consumer Wi-Fi blocks device-to-device traffic).

```bash
adb pair <pair-ip>:<pair-port>      # 6-digit code expires in ~60s
adb connect <connect-ip>:<connect-port>
adb devices                          # confirm "device" status
```

### Emulator

The Wear OS **5** AVD's picker hides AndroidX legacy faces (system message: "Unsupported legacy watch face"). Use a **Wear OS 4 (API 33) AVD** instead — its picker accepts our face. Real Galaxy Watch 6 Classic also works regardless of OS version (Samsung's One UI Watch picker accepts legacy faces).

The renderer auto-detects the AVD via `Build.FINGERPRINT/MODEL/HARDWARE` and switches to **screenshot mode**: compass arc rendered statically (no sensor jitter), step count shows a placeholder `17,456`. Same APK runs unchanged on real hardware.

## Open issues — see [`HANDOFF.md`](HANDOFF.md)

- Complications work (slots wire correctly, no bind timeout) but the picker UI to fill them is hidden in Galaxy Watch's One UI Watch — needs a custom editor activity
- Steps relies on `TYPE_STEP_COUNTER`, which Samsung's firmware batches unpredictably — should swap for the Health Services API (`androidx.health:health-services-client`)
- Compass is disabled on real watch because the magnetometer is corrupted by the metallic rotating bezel — needs a calibration UX
- Picker thumbnail is a placeholder vector — needs a real face render

## Credits

OpenDyslexic font under SIL Open Font License, redistributable.
