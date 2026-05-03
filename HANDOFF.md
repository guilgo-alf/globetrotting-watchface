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

### 3. Real preview thumbnail for the picker

**Problem.** `app/src/main/res/drawable/preview.xml` is a placeholder vector (black square + 5 white bars). When the user opens the watch-face picker, our face shows up with this generic icon instead of a real thumbnail of the design.

**Expected outcome.** The picker thumbnail is a representative render of the watch face — big time, status line, date, ideally compass arc.

**Likely involves.** Replacing `preview.xml` with `preview.png` (or keeping it as a vector drawable but actually drawing the face). Could either programmatically render the face once at build time, take a screenshot from the AVD, or hand-design a representative SVG.

**Acceptance test.** Watch face picker on the Galaxy Watch 6 Classic shows a thumbnail that visibly matches the live face design.

---

### 4. (Optional) Compass calibration UX

**Status.** Disabled on real watch in `GlobetrottingRenderer` (`enableCompass = isPreviewEmulator`). Galaxy Watch 6 Classic's metallic rotating bezel sits millimetres from the magnetometer and produces unreliable readings without manual figure-8 calibration. We can re-enable for screenshot mode (works there) but it floats wildly on real wrist.

**Expected outcome (if you take this on).** Compass arc functional on real hardware. Probably needs:
- An accuracy listener that surfaces `SENSOR_STATUS_UNRELIABLE` / `SENSOR_STATUS_ACCURACY_LOW` to the user via a small UI hint ("calibrate compass" overlay)
- Possibly heavier smoothing or a different sensor source
- Possibly a one-time onboarding asking the user to do figure-8

**Lowest priority.** If items 1-3 cost more than expected, skip this one and just leave it disabled.

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
