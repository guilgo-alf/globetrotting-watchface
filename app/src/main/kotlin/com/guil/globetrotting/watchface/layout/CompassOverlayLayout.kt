package com.guil.globetrotting.watchface.layout

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.guil.globetrotting.watchface.RenderState
import com.guil.globetrotting.watchface.data.WatchFaceColors
import com.guil.globetrotting.watchface.data.WatchFaceMetrics

/**
 * Thin red stroked arc on the bezel marking magnetic north. The arc shape itself is
 * static; we rotate the canvas around the watch centre by the bearing each frame.
 *
 * Rendering:
 *   1. Translate canvas origin to watch centre
 *   2. Rotate by bearing degrees (0° = north stays at top, 90° rotates to 3 o'clock, …)
 *   3. drawArc on a centred oval — start at -90 - sweep/2 (i.e. 12 o'clock), sweep SWEEP_DEG
 */
class CompassOverlayLayout {

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WatchFaceColors.NORTH_RED
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val arcBounds: RectF = RectF(-ARC_RADIUS, -ARC_RADIUS, ARC_RADIUS, ARC_RADIUS)

    fun draw(canvas: Canvas, state: RenderState) {
        if (state.isAmbient) return
        val bearing = state.compassBearingDeg ?: return
        if (bearing.isNaN()) return

        canvas.save()
        canvas.translate(WatchFaceMetrics.CENTRE, WatchFaceMetrics.CENTRE)
        canvas.rotate(bearing)
        canvas.drawArc(
            arcBounds,
            -90f - SWEEP_DEG / 2f,
            SWEEP_DEG,
            /* useCenter = */ false,
            markerPaint,
        )
        canvas.restore()
    }

    companion object {
        // 222: 6 px further from centre per real-watch feedback. Still well inside
        // the round mask (240 radius - 222 = 18 px clearance) so no clipping.
        private const val ARC_RADIUS = 222f
        // 13.5° (10% shorter than the previous 15°) — even tighter arc segment.
        private const val SWEEP_DEG = 13.5f
        private const val STROKE_WIDTH = 480f * 0.013f
    }
}
