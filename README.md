# Globetrotting Minimalist

Personal Wear OS watch face for Galaxy Watch 6 Classic 47mm. Native Kotlin /
`Renderer.CanvasRenderer2`, built incrementally as a series of vertical-slice
sessions. Sideload only.

See [CLAUDE.md](CLAUDE.md) for the full design brief and session plan.

## Status

All 5 sessions shipped. Watch face has:

- Status line: `steps · tempC · watchBattery% · phoneBattery%`
- Big time digits (24 h, no leading zero, no colon)
- Tiny seconds superscript flush-right of the digits
- Date `EEE dd` centred below
- Compass marker (amber dot) tracks magnetic north on the bezel ring
- Tile (swipe-left) shows three bonus timezones (Tokyo/Mexico/London), steps with goal, watch + phone battery, long-form date

Weather and phone battery flow in via complication slots — long-press the face → Customize → pick a data source for each. AOD switches automatically.

The local Wear OS **5** emulator's picker hides legacy AndroidX faces (see `CLAUDE.md` § 11b); develop against an API 33 AVD or push to the real Galaxy Watch.

## Build & install

One-time setup:

```bash
./scripts/make-keystore.sh    # creates ~/.android/globetrotting-debug.keystore
```

Per-session loop:

```bash
./gradlew assembleRelease && ./scripts/install.sh
```

The APK lands at `app/build/outputs/apk/release/app-release.apk`. After the
first install, long-press your current watch face -> scroll to "Globetrotting
Minimalist" -> tap.

## Connecting to the watch

Home Wi-Fi (KPN Box 12b) blocks device-to-device traffic — always use the phone
hotspot for sideloading.

```bash
adb pair <watch-ip>:<pair-port>      # one-time, code expires in ~60s
adb connect <watch-ip>:5555
adb devices                           # confirm "device" status
```

## Stack

- Kotlin only, Gradle Kotlin DSL
- `androidx.wear.watchface:watchface:1.2.1`
- `Renderer.CanvasRenderer2` (NOT Watch Face Format — we need raw sensor access)
- `java.time` for dates / timezones (DST handled natively)
- `Sensor.TYPE_ROTATION_VECTOR` for compass (planned, session 4)
- Min SDK 33, target SDK 35
- Single font: OpenDyslexic-Regular (SIL OFL, redistributable)
