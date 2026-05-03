package com.guil.globetrotting.watchface

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.guil.globetrotting.compass.CompassSensorManager
import com.guil.globetrotting.stats.BatteryProvider
import com.guil.globetrotting.stats.StepsProvider
import com.guil.globetrotting.watchface.data.WatchFaceColors
import com.guil.globetrotting.watchface.data.WatchFaceMetrics
import com.guil.globetrotting.watchface.layout.BigTimeLayout
import com.guil.globetrotting.watchface.layout.CompassOverlayLayout
import com.guil.globetrotting.watchface.layout.StatusLineLayout
import com.guil.globetrotting.watchface.layout.TimezoneGridLayout
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class GlobetrottingRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    currentUserStyleRepository: CurrentUserStyleRepository,
    private val watchStateRef: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
) : Renderer.CanvasRenderer2<GlobetrottingRenderer.GlobeAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchStateRef,
    CanvasType.HARDWARE,
    INTERACTIVE_FRAME_PERIOD_MS,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false,
) {

    class GlobeAssets : SharedAssets {
        override fun onDestroy() {}
    }

    private val statusLineLayout = StatusLineLayout(context)
    private val bigTimeLayout = BigTimeLayout(context)
    private val timezoneGridLayout = TimezoneGridLayout(context)
    private val compassOverlayLayout = CompassOverlayLayout()

    private val batteryProvider = BatteryProvider(context)
    private val stepsProvider = StepsProvider(context)
    private val compassSensorManager = CompassSensorManager(context)

    // Default to north (0°) so the arc is visible at the top of the canvas immediately,
    // even before the rotation-vector sensor fires its first event. On a real watch the
    // first sensor reading overrides this within ~50 ms.
    @Volatile private var currentBearing: Float? = 0f
    private var sensorsActive = false
    // Set true in onDestroy so any in-flight sensor callback that survives the
    // unregister race exits before calling invalidate() on a destroyed renderer.
    @Volatile private var destroyed = false

    // Detects the Android Studio AVD (any Wear OS emulator) — used to enable
    // "preview / screenshot mode" so screenshots show feature-complete state
    // (compass visible at static 0°, believable step count) regardless of what
    // the real sensors are doing.
    private val isPreviewEmulator: Boolean =
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
            Build.MODEL.contains("sdk", ignoreCase = true) ||
            Build.HARDWARE == "ranchu" ||
            Build.HARDWARE == "goldfish"

    // Render the compass arc?
    //   - Emulator: yes, frozen at initial 0° (12 o'clock) — screenshots show the
    //     feature without sensor jitter
    //   - Real watch: no — Galaxy Watch 6 Classic's metallic bezel sits millimetres
    //     from the magnetometer and gives unreliable readings without figure-8
    //     calibration; disabled until proper calibration UX exists
    private val enableCompass = isPreviewEmulator
    // Register the magnetometer? Always false for now: emulator screenshots want
    // a static arc, real watch is fighting the calibration issue. Flip on later.
    private val registerCompassSensor = false

    // Don't redraw on sub-degree noise — one screen pixel of arc movement at r=216
    // corresponds to ~0.27°, so 0.5° is the smallest change worth invalidating for.
    private val MIN_BEARING_DELTA_DEG = 0.5f

    override suspend fun createSharedAssets(): GlobeAssets = GlobeAssets()

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: GlobeAssets) {
        val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT
        syncSensors(active = watchStateRef.isVisible.value == true && !isAmbient)

        canvas.drawColor(WatchFaceColors.BACKGROUND)
        val state = buildRenderState(zonedDateTime, isAmbient)

        // Scale our 480-design coordinate space to whatever the actual canvas size is
        // (480 on a real Galaxy Watch 6, 454 on Wear OS Large Round AVD). Without this
        // scale, hardcoded centres at 240,240 sit off-centre on smaller canvases.
        val sx = canvas.width / WatchFaceMetrics.CANVAS.toFloat()
        val sy = canvas.height / WatchFaceMetrics.CANVAS.toFloat()
        canvas.save()
        canvas.scale(sx, sy)
        statusLineLayout.draw(canvas, state)
        bigTimeLayout.draw(canvas, state)
        timezoneGridLayout.draw(canvas, state)
        if (enableCompass) compassOverlayLayout.draw(canvas, state)
        canvas.restore()
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: GlobeAssets) {
        canvas.drawColor(WatchFaceColors.BACKGROUND)
    }

    override fun onDestroy() {
        destroyed = true
        syncSensors(active = false)
        super.onDestroy()
    }

    private fun syncSensors(active: Boolean) {
        if (active && !sensorsActive) {
            stepsProvider.start()
            if (registerCompassSensor) {
                compassSensorManager.start { bearing ->
                    // Defensive: if onDestroy raced with an in-flight sensor event, exit
                    // before invalidate() — the renderer's surface may be gone.
                    if (destroyed) return@start
                    val previous = currentBearing
                    currentBearing = bearing
                    // Skip invalidate for tiny bearing jitter so we don't redraw 60 fps
                    // on sensor noise — only change > 0.5° actually moves the arc visibly.
                    val delta = if (previous == null) Float.MAX_VALUE
                    else angularDelta(previous, bearing)
                    if (delta >= MIN_BEARING_DELTA_DEG) invalidate()
                }
            }
            sensorsActive = true
        } else if (!active && sensorsActive) {
            stepsProvider.stop()
            if (registerCompassSensor) compassSensorManager.stop()
            // Keep last known bearing — don't null it. This way the arc still renders
            // pointing somewhere when the face wakes up before the sensor fires again.
            sensorsActive = false
        }
    }

    /** Shortest angular distance between two bearings in degrees, [0, 180]. */
    private fun angularDelta(a: Float, b: Float): Float {
        var d = Math.abs(a - b) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    private fun buildRenderState(zdt: ZonedDateTime, isAmbient: Boolean): RenderState {
        val localZdt = ZonedDateTime.ofInstant(Instant.from(zdt), ZoneId.systemDefault())
        // Steps:
        //   - Emulator → believable placeholder (17,456) for screenshot purposes
        //   - Real watch with sensor → live count (even 0 is truthful)
        //   - Hardware without sensor → fallback placeholder
        val steps = when {
            isPreviewEmulator -> 17_456
            stepsProvider.hasSensor() -> stepsProvider.stepsToday()
            else -> 17_456
        }
        // Read complication slots (weather + phone battery). Slots are user-fillable
        // via the long-press editor on real watch — Phone Hub for phone battery,
        // Google Weather (or similar) for temperature. On the emulator the slots are
        // empty, so the values fall back to placeholders so the design reads.
        val weatherC = readShortTextOrRangedAsInt(ComplicationSlots.WEATHER_SLOT_ID)
            ?: EMULATOR_PLACEHOLDER_WEATHER_C
        val phoneBattery = readShortTextOrRangedAsInt(ComplicationSlots.PHONE_BATTERY_SLOT_ID)
            ?: EMULATOR_PLACEHOLDER_PHONE_BATTERY
        return RenderState(
            now = localZdt,
            watchBatteryPct = batteryProvider.currentPercent(),
            stepCount = steps,
            phoneBatteryPct = phoneBattery,
            weatherTempC = weatherC,
            compassBearingDeg = currentBearing,
            isAmbient = isAmbient,
        )
    }

    /**
     * Pull the int value out of a complication slot. Handles both common shapes:
     *   - RANGED_VALUE: e.g. battery percentage (returns the float value rounded)
     *   - SHORT_TEXT: e.g. weather "22°C" — strips the unit and parses the digits
     * Returns null if the slot is empty, the data type is something else, or parsing fails.
     */
    private fun readShortTextOrRangedAsInt(slotId: Int): Int? {
        val slot = complicationSlotsManager.complicationSlots[slotId] ?: return null
        return when (val data = slot.complicationData.value) {
            is RangedValueComplicationData -> data.value.toInt()
            is ShortTextComplicationData -> {
                val raw = data.text.getTextAt(context.resources, Instant.now()).toString()
                // Extract leading number, ignoring "°C", "%", whitespace etc.
                Regex("-?\\d+").find(raw)?.value?.toIntOrNull()
            }
            else -> null
        }
    }

    companion object {
        private const val INTERACTIVE_FRAME_PERIOD_MS = 1_000L
        // Used when the corresponding complication slot is empty (e.g. on the
        // emulator where no provider is installed). On real watch the user fills
        // the slots via long-press → Customize.
        private const val EMULATOR_PLACEHOLDER_WEATHER_C = 22
        private const val EMULATOR_PLACEHOLDER_PHONE_BATTERY = 65
    }
}
