package com.guil.globetrotting.watchface

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
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
    context: Context,
    surfaceHolder: SurfaceHolder,
    currentUserStyleRepository: CurrentUserStyleRepository,
    private val watchStateRef: WatchState,
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
        compassOverlayLayout.draw(canvas, state)
        canvas.restore()
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: GlobeAssets) {
        canvas.drawColor(WatchFaceColors.BACKGROUND)
    }

    override fun onDestroy() {
        syncSensors(active = false)
        super.onDestroy()
    }

    private fun syncSensors(active: Boolean) {
        if (active && !sensorsActive) {
            stepsProvider.start()
            compassSensorManager.start { bearing ->
                val previous = currentBearing
                currentBearing = bearing
                // Skip invalidate for tiny bearing jitter so we don't redraw 60 fps
                // on sensor noise — only change > 0.5° actually moves the arc visibly.
                val delta = if (previous == null) Float.MAX_VALUE
                else angularDelta(previous, bearing)
                if (delta >= MIN_BEARING_DELTA_DEG) invalidate()
            }
            sensorsActive = true
        } else if (!active && sensorsActive) {
            stepsProvider.stop()
            compassSensorManager.stop()
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
        // TEMP for emulator preview — fills the status line with believable values
        // since the emulator has no step sensor and no complication providers wired.
        // Replace with real provider/complication data on the real watch.
        val stepsForPreview = stepsProvider.stepsToday().takeIf { it > 0 } ?: 1_445
        val tempForPreview = 22
        val phoneBatteryForPreview = 20
        return RenderState(
            now = localZdt,
            watchBatteryPct = batteryProvider.currentPercent(),
            phoneBatteryPct = phoneBatteryForPreview,
            stepCount = stepsForPreview,
            weatherTempC = tempForPreview,
            compassBearingDeg = currentBearing,
            isAmbient = isAmbient,
        )
    }

    companion object {
        private const val INTERACTIVE_FRAME_PERIOD_MS = 1_000L
    }
}
