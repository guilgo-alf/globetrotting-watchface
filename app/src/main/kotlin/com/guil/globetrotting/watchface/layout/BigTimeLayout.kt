package com.guil.globetrotting.watchface.layout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.guil.globetrotting.R
import com.guil.globetrotting.watchface.RenderState
import com.guil.globetrotting.watchface.data.WatchFaceColors
import com.guil.globetrotting.watchface.data.WatchFaceMetrics
import java.time.format.DateTimeFormatter

class BigTimeLayout(context: Context) {

    private val typeface: Typeface = ResourcesCompat.getFont(context, R.font.opendyslexic_regular)
        ?: Typeface.SANS_SERIF

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = this@BigTimeLayout.typeface
        textSize = WatchFaceMetrics.BIG_TIME_SIZE_PX
        letterSpacing = WatchFaceMetrics.BIG_TIME_TRACKING / WatchFaceMetrics.BIG_TIME_SIZE_PX
        textScaleX = WatchFaceMetrics.BIG_TIME_SCALE_X
        textAlign = Paint.Align.CENTER
        color = WatchFaceColors.FOREGROUND
        // OpenDyslexic at hero size benefits from grayscale hinting + AA only;
        // sub-pixel hints emphasize the irregular glyph contours at this scale.
        isSubpixelText = false
        hinting = Paint.HINTING_OFF
    }

    private val secondsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = this@BigTimeLayout.typeface
        textSize = WatchFaceMetrics.SECONDS_SUPER_SIZE_PX
        textAlign = Paint.Align.LEFT
        color = WatchFaceColors.SUBTLE
    }

    fun draw(canvas: Canvas, state: RenderState) {
        timePaint.color = if (state.isAmbient) WatchFaceColors.FOREGROUND_AMBIENT else WatchFaceColors.FOREGROUND

        val timeText = state.now.format(TIME_FORMAT)
        val metrics = timePaint.fontMetrics
        val baselineY = WatchFaceMetrics.BIG_TIME_CENTRE_Y - (metrics.ascent + metrics.descent) / 2f

        // Digits centred at canvas X.
        canvas.drawText(timeText, WatchFaceMetrics.BIG_TIME_CENTRE_X, baselineY, timePaint)

        if (!state.isAmbient) {
            val timeWidth = timePaint.measureText(timeText)
            val rightEdge = WatchFaceMetrics.BIG_TIME_CENTRE_X + timeWidth / 2f
            val secondsText = state.now.format(SECONDS_FORMAT)
            // Vertically centre seconds with the big-time digits — sits at canvas centre Y.
            val secondsMetrics = secondsPaint.fontMetrics
            val secondsBaseline = WatchFaceMetrics.BIG_TIME_CENTRE_Y -
                (secondsMetrics.ascent + secondsMetrics.descent) / 2f
            canvas.drawText(secondsText, rightEdge + WatchFaceMetrics.SECONDS_GAP_PX, secondsBaseline, secondsPaint)
        }
    }

    companion object {
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("Hmm")
        private val SECONDS_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("ss")
    }
}
