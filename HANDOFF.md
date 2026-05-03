# Handoff brief

You're being hired to fix four specific issues in this codebase. **The repo works** — long sideload sessions to a real Galaxy Watch 6 Classic verified the watch face binds, renders, and survives AOD/interactive transitions. The remaining problems are specifically about Wear OS UX integrations that take experience to get right.

**Read `README.md` first** for layout and stack. Read `CLAUDE.md` for full architectural context and decision history (~7 sessions of incremental design). The whole codebase is intentionally small — under 1500 lines of Kotlin total.

## Hard constraints (read these first)

1. **Do not rewrite the architecture.** The renderer, layouts, palette, band visualisation, dim-minutes rule, deep-work dot logic, complication slot wiring — all of it is the result of long iterative wrist-feedback sessions. Extend, don't replace.

2. **Do not port to WFF (Watch Face Format).** It's where Wear OS is heading, but it loses raw sensor access (kills the compass) and disables programmatic rendering (kills the band visualisation). This is a personal sideload-only project; legacy AndroidX stays.

3. **Do not add complications beyond the two existing slots** (`WEATHER_SLOT_ID = 1`, `PHONE_BATTERY_SLOT_ID = 2` in `ComplicationSlots.kt`).

4. **Do not modify the visual design** without checking. The typography, layout positions, colours, dim rules, and asymmetric deep-work dot timing (black 09:00 → 16:30) are all opinionated final calls.

5. **Sideload-only**, no Play Store submission, no marketing, no listings.

---

## Tasks (in priority order)

Tasks 1, 2, 5 are must-have. Tasks 3, 4 are added value (the typography matters to the owner). Task 6 is optional, only if the others come in under budget.

### 1. Customize flow on Galaxy Watch 6 Classic 🔥 (priority)

**Problem.** The watch face declares 2 invisible complication slots (`ComplicationSlots.kt`). They wire correctly — `dumpsys` confirms the slots exist with `provider=null`. The system editor never appears in Samsung One UI Watch's long-press flow on the real watch. This means the slots can never be filled, so the renderer always shows the placeholder values (`22°C` / `65`).

**Expected outcome.** When the user long-presses the active face on the Galaxy Watch 6 Classic, they get to a Customize/Edit screen that lets them tap each of the two status-line slots and pick a complication source from whatever's installed (Phone Hub, Phone Battery Complication, Google Weather, AccuWeather, etc.).

**Likely involves.** Building a custom editor activity using `androidx.wear.watchface.editor.WatchFaceEditorContract` (or whatever Samsung One UI Watch needs to surface the picker), declaring it in `AndroidManifest.xml` with the right `<intent-filter>`, and possibly adding metadata so the launcher picks it up.

**Acceptance test.** After install on the real Galaxy Watch 6 Classic, long-pressing the active face surfaces a way to pick a complication source for each of the two slots. Picked sources persist across face-switches and reboots. The renderer then displays the live values (already wired in `GlobetrottingRenderer.readShortTextOrRangedAsInt`).

---

### 2. Replace `StepsProvider` with Health Services Client

**Problem.** Current `StepsProvider` registers `Sensor.TYPE_STEP_COUNTER` directly. On Samsung Galaxy Watch the step-counter sensor batches/throttles aggressively and sometimes goes 30+ minutes without firing even during walking. The displayed step count lags reality badly.

**Expected outcome.** The status-line `steps` value reflects the user's actual step count for the day, updating within ~1 minute of each step batch.

**Likely involves.** Adding `androidx.health:health-services-client` to the dependencies, swapping `StepsProvider` to use `PassiveMonitoringClient` with `DataType.STEPS_DAILY` (or equivalent), wiring lifecycle (start in `GlobetrottingRenderer.syncSensors`, stop on hide / destroy).

**Keep:** the `hasSensor()` method signature so `GlobetrottingRenderer.buildRenderState` keeps falling back to the placeholder when the API is unavailable (e.g. on AVD).

**Acceptance test.** On the real Galaxy Watch 6 Classic, after walking ~20 visible steps, the status line `steps` value reflects the new total within a minute.

---

### 3. Watch face font picker — OpenDyslexic vs One UI Sans

**Problem.** The watch face currently uses OpenDyslexic-Regular as a single bundled font across all layouts (`StatusLineLayout`, `BigTimeLayout`, `TimezoneGridLayout`, `CompassOverlayLayout`). Owner wants to be able to switch between **OpenDyslexic** (current default) and **One UI Sans** (Samsung's system default sans-serif) via the system Customize editor.

**Expected outcome.** A user-style option visible in long-press → Customize on the real Galaxy Watch 6 Classic. Two choices, OpenDyslexic and One UI Sans. Selection persists across face-switches and reboots, applied to every text element on the face.

**Likely involves.** Adding a `UserStyleSchema.ListUserStyleSetting` to `GlobetrottingWatchFaceService.createUserStyleSchema()` (currently returns `UserStyleSchema(emptyList())`), threading the current style choice from `currentUserStyleRepository` to `GlobetrottingRenderer`, and swapping the `Typeface` in each layout's `Paint` based on the active option. One UI Sans on a Samsung device is just `Typeface.DEFAULT` — no font asset needed; only OpenDyslexic loads from `res/font/`.

**Note.** This depends on task 1 — without the editor activity exposed, the option won't be reachable from the watch UI. Build task 1 first.

**Acceptance test.** Long-press → Customize on the real Galaxy Watch 6 Classic surfaces a "Font" option with two choices. Picking each visibly switches the watch face typography (time digits, status line, date label all change). Choice survives a reboot.

---

### 4. Two tile variants — One UI Sans + OpenDyslexic

**Problem.** The tile currently uses ProtoLayout's default font, which on Samsung devices renders as One UI Sans. Owner wants a *second* tile registered alongside it that renders the same content with OpenDyslexic typography, so the tile carousel offers both as separately selectable tiles.

**Constraint — read this first.** ProtoLayout has **no custom-font API**. Tile rendering happens in the system process (`com.google.android.wearable.app`), which has no access to the APK's `res/font/` resources. Verified against `androidx.wear.protolayout:1.4.0` and the AndroidX main branch — `ResourceBuilders.Resources` only registers images, not fonts. `FontStyle.setPreferredFontFamilies()` only accepts system names (`"roboto"` / `"roboto-flex"`); custom names are ignored.

**Likely involves.** Either:
- **(a)** Render each text element of the tile as a bitmap (using `android.graphics.Paint` + `Typeface.createFromAsset(...)` to load OpenDyslexic from APK assets), then ship via `InlineImageResource` registered in `onTileResourcesRequest`. The existing `ExtrasTileRenderer.bandResource()` is a starting reference for the bitmap-and-register pattern. Substantial work because every `Text` element becomes an `Image`, and you lose ProtoLayout's auto layout for text.
- **(b)** A different approach you know that we don't — open to ideas if there's something we missed.

**Architecture.** Two distinct `TileService` classes registered in the manifest (e.g. `ExtrasTileService` for One UI Sans, `ExtrasTileServiceDyslexic` for OpenDyslexic) is probably cleanest, sharing the bulk of the rendering logic. Each tile gets its own label so they appear as separate options in the carousel.

**Acceptance test.** Tile carousel on the Galaxy Watch 6 Classic shows both variants. The OpenDyslexic variant renders all timezone codes, hours, offsets in OpenDyslexic typography. Both render in interactive mode and don't crash on AOD transitions.

---

### 5. Real preview thumbnail for the picker

**Problem.** `app/src/main/res/drawable/preview.xml` is a placeholder vector (black square + 5 white bars). When the user opens the watch-face picker, our face shows up with this generic icon instead of a real thumbnail of the design.

**Expected outcome.** The picker thumbnail is a representative render of the watch face — big time, status line, date, ideally compass arc.

**Likely involves.** Replacing `preview.xml` with `preview.png` (or keeping it as a vector drawable but actually drawing the face). Could either programmatically render the face once at build time, take a screenshot from the AVD, or hand-design a representative SVG.

**Acceptance test.** Watch face picker on the Galaxy Watch 6 Classic shows a thumbnail that visibly matches the live face design.

---

### 6. (Optional) Compass calibration UX

**Status.** Disabled on real watch in `GlobetrottingRenderer` (`enableCompass = isPreviewEmulator`). Galaxy Watch 6 Classic's metallic rotating bezel sits millimetres from the magnetometer and produces unreliable readings without manual figure-8 calibration. We can re-enable for screenshot mode (works there) but it floats wildly on real wrist.

**Expected outcome (if you take this on).** Compass arc functional on real hardware. Probably needs:
- An accuracy listener that surfaces `SENSOR_STATUS_UNRELIABLE` / `SENSOR_STATUS_ACCURACY_LOW` to the user via a small UI hint ("calibrate compass" overlay)
- Possibly heavier smoothing or a different sensor source
- Possibly a one-time onboarding asking the user to do figure-8

**Lowest priority.** If items 1-5 cost more than expected, skip this one and just leave it disabled.

**Acceptance test.** Compass arc points consistently towards magnetic north on real hardware after a figure-8 calibration prompt; doesn't drift more than ~5° while stationary.

---

## How to run / test

```bash
# build release APK (signed with the dev keystore — generate it once via scripts/make-keystore.sh)
./gradlew assembleRelease

# install onto a connected device (real Galaxy Watch 6 Classic via adb-over-Wi-Fi recommended)
./scripts/install.sh
```

The install script also grants `ACTIVITY_RECOGNITION` (required for the step counter to receive events). On the watch: long-press → scroll picker → tap **Globetrotter v2**.

For the Customize task (#1), please test on **real Galaxy Watch 6 Classic hardware**. The Wear OS AVD's picker hides AndroidX legacy faces entirely (`WFInfoResolver Unsupported legacy watch face`); use an **API 33 (Wear OS 4) AVD** if you need an emulator, but Samsung One UI's specific quirks won't surface there.

For the Health Services task (#2), the AVD is fine — Health Services has emulator support.

---

## What to deliver

- Source changes as PRs / branches against this repo
- A short note (in the PR description or a `NOTES.md` you create) summarising what you changed and why, especially for the Customize task — what was missing, why it's now there
- Screenshots or screen recordings from the real Galaxy Watch 6 Classic verifying acceptance for each task
- An updated `CLAUDE.md` ("Session 8" or whatever number) recording the work done so the next person picking this up has context
