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
        if (state.isAmbient) return

        val text = formatStatusLine(state)
        val metrics = paint.fontMetrics
        val baselineY = WatchFaceMetrics.STATUS_LINE_CENTRE_Y - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, WatchFaceMetrics.STATUS_LINE_CENTRE_X, baselineY, paint)
    }

    private fun formatStatusLine(state: RenderState): String {
        // Four-field format: steps · weather · watchBattery · phoneBattery.
        // Real on the watch: stepCount + watchBatteryPct. Placeholder until DataLayer
        // (phone battery) and a Complication slot (weather) get wired — see RenderState.
        // Nullable fields fall back to "--" so the row never disappears or shifts.
        val steps = String.format(java.util.Locale.ENGLISH, "%,d", state.stepCount)
        val temp = state.weatherTempC?.let { "${it}°C" } ?: "--°C"
        val watch = "${state.watchBatteryPct}%"
        val phone = state.phoneBatteryPct?.let { "${it}%" } ?: "--%"
        return "$steps $SEP $temp $SEP $watch $SEP $phone"
    }

    companion object {
        private const val SEP = "·"
    }
}
