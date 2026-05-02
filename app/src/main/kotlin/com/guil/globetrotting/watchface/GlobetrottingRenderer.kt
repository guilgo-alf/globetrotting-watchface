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

    @Volatile private var currentBearing: Float? = null
    private var sensorsActive = false

    override suspend fun createSharedAssets(): GlobeAssets = GlobeAssets()

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: GlobeAssets) {
        val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT
        syncSensors(active = watchStateRef.isVisible.value == true && !isAmbient)

        canvas.drawColor(WatchFaceColors.BACKGROUND)
        val state = buildRenderState(zonedDateTime, isAmbient)
        statusLineLayout.draw(canvas, state)
        bigTimeLayout.draw(canvas, state)
        timezoneGridLayout.draw(canvas, state)
        compassOverlayLayout.draw(canvas, state)
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
                currentBearing = bearing
                invalidate()
            }
            sensorsActive = true
        } else if (!active && sensorsActive) {
            stepsProvider.stop()
            compassSensorManager.stop()
            currentBearing = null
            sensorsActive = false
        }
    }

    private fun buildRenderState(zdt: ZonedDateTime, isAmbient: Boolean): RenderState {
        val localZdt = ZonedDateTime.ofInstant(Instant.from(zdt), ZoneId.systemDefault())
        return RenderState(
            now = localZdt,
            watchBatteryPct = batteryProvider.currentPercent(),
            phoneBatteryPct = null,
            stepCount = stepsProvider.stepsToday(),
            weatherTempC = null,
            compassBearingDeg = currentBearing,
            isAmbient = isAmbient,
        )
    }

    companion object {
        private const val INTERACTIVE_FRAME_PERIOD_MS = 1_000L
    }
}
