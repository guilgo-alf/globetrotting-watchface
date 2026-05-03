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

class StatusLineLayout(context: Context) {

    private val typeface: Typeface = ResourcesCompat.getFont(context, R.font.opendyslexic_regular)
        ?: Typeface.SANS_SERIF

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = this@StatusLineLayout.typeface
        textSize = WatchFaceMetrics.STATUS_LINE_SIZE_PX
        textAlign = Paint.Align.CENTER
        color = WatchFaceColors.MUTED
    }

    fun draw(canvas: Canvas, state: RenderState) {
        // AOD: render at FAINT colour so the status line still appears in always-on
        // mode but doesn't dominate. Same pattern the timezone grid uses for AOD.
        paint.color = if (state.isAmbient) WatchFaceColors.FAINT else WatchFaceColors.MUTED
        val text = formatStatusLine(state)
        val metrics = paint.fontMetrics
        val baselineY = WatchFaceMetrics.STATUS_LINE_CENTRE_Y - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, WatchFaceMetrics.STATUS_LINE_CENTRE_X, baselineY, paint)
    }

    private fun formatStatusLine(state: RenderState): String {
        // Four-field format: steps · weather · watchBattery · phoneBattery.
        // Battery percentages render as bare numbers — no `%` suffix — matching the
        // original Facer reference. The °C on the temp field is the only unit; the
        // bare numbers read as "context-implied percentages".
        val steps = String.format(java.util.Locale.ENGLISH, "%,d", state.stepCount)
        val temp = state.weatherTempC?.let { "${it}°C" } ?: "--°C"
        val watch = state.watchBatteryPct.toString()
        val phone = state.phoneBatteryPct?.toString() ?: "--"
        return "$steps $SEP $temp $SEP $watch $SEP $phone"
    }

    companion object {
        private const val SEP = "·"
    }
}
