package com.guil.globetrotting.watchface.layout

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.guil.globetrotting.watchface.RenderState
import com.guil.globetrotting.watchface.data.WatchFaceColors
import com.guil.globetrotting.watchface.data.WatchFaceMetrics

/**
 * Thin red stroked arc inside the black watch face area, ~12° of sweep centred on
 * magnetic north. Rotates around the centre as the wrist rotates. No fill, no glow.
 *
 * Reference: ~93% of canvas radius, ~12° sweep, stroke ~0.7% of canvas diameter
 * (scaled from a 1.4 px reference at 200 px diameter).
 */
class CompassOverlayLayout {

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WatchFaceColors.NORTH_RED
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        strokeCap = Paint.Cap.ROUND
    }

    /**
     * Bounding box of the *full circle* the arc lives on — Canvas.drawArc uses this
     * with start/sweep angles (in degrees, 0° = 3 o'clock, increasing clockwise).
     */
    private val arcBounds: RectF = RectF(
        WatchFaceMetrics.CENTRE - ARC_RADIUS,
        WatchFaceMetrics.CENTRE - ARC_RADIUS,
        WatchFaceMetrics.CENTRE + ARC_RADIUS,
        WatchFaceMetrics.CENTRE + ARC_RADIUS,
    )

    fun draw(canvas: Canvas, state: RenderState) {
        if (state.isAmbient) return
        val bearing = state.compassBearingDeg ?: return

        // bearing 0° (north) should appear at 12 o'clock (Canvas angle 270°, i.e. -90°).
        // Canvas drawArc startAngle is the position of the arc's leading edge, measured
        // clockwise from 3 o'clock. We want the arc *centred* on the heading, so the
        // start angle is heading − 90 − sweep/2.
        val startAngle = bearing - 90f - SWEEP_DEG / 2f
        canvas.drawArc(arcBounds, startAngle, SWEEP_DEG, false, markerPaint)
    }

    companion object {
        // Inner edge of the visible black face is ~r=220 on a 480-canvas. 93 % keeps
        // the arc just inside it with a little margin to the bezel.
        private const val ARC_RADIUS = 220f * 0.93f
        private const val SWEEP_DEG = 12f
        private const val STROKE_WIDTH = 480f * 0.013f
    }

    // Path retained for potential future custom path rendering — currently unused.
    @Suppress("unused")
    private val unusedPath = Path()
}
