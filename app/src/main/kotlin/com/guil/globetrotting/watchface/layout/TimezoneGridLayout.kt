package com.guil.globetrotting.watchface.layout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.guil.globetrotting.R
import com.guil.globetrotting.watchface.RenderState
import com.guil.globetrotting.watchface.data.CellContent
import com.guil.globetrotting.watchface.data.GRID_CELLS
import com.guil.globetrotting.watchface.data.GridMetrics
import com.guil.globetrotting.watchface.data.TimezoneRow
import com.guil.globetrotting.watchface.data.WatchFaceColors
import com.guil.globetrotting.watchface.data.WatchFaceMetrics
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimezoneGridLayout(context: Context) {

    private val typeface: Typeface = ResourcesCompat.getFont(context, R.font.opendyslexic_regular)
        ?: Typeface.SANS_SERIF

    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = this@TimezoneGridLayout.typeface
        textSize = WatchFaceMetrics.TIMEZONE_ROW_SIZE_PX
        textAlign = Paint.Align.RIGHT
        color = WatchFaceColors.MUTED
    }

    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = this@TimezoneGridLayout.typeface
        textSize = WatchFaceMetrics.DATE_LABEL_SIZE_PX
        textAlign = Paint.Align.CENTER
        color = WatchFaceColors.FOREGROUND
    }

    fun draw(canvas: Canvas, state: RenderState) {
        zonePaint.color = if (state.isAmbient) WatchFaceColors.FAINT else WatchFaceColors.MUTED
        datePaint.color = if (state.isAmbient) WatchFaceColors.FOREGROUND_AMBIENT else WatchFaceColors.FOREGROUND

        for (cell in GRID_CELLS) {
            val baselineY = GridMetrics.ORIGIN_Y + cell.row * GridMetrics.ROW_HEIGHT

            when (val content = cell.content) {
                is CellContent.Zone -> {
                    val rightX = GridMetrics.ORIGIN_X +
                        cell.col * (GridMetrics.COL_WIDTH + GridMetrics.GUTTER) +
                        GridMetrics.COL_WIDTH
                    canvas.drawText(formatZone(state, content.zone), rightX, baselineY, zonePaint)
                }
                CellContent.DateLabel -> {
                    canvas.drawText(formatDate(state), WatchFaceMetrics.CENTRE, baselineY, datePaint)
                }
            }
        }
    }

    /**
     * Compact cell format: `H ABBR±N` or `Hmm ABBR±N.N` for half-hour zones.
     * Trades the brief's `// ` separator for a sign-prefixed offset to fit the 98-px
     * column width at 13-px OpenDyslexic.
     */
    private fun formatZone(state: RenderState, zone: TimezoneRow): String {
        val target = state.now.withZoneSameInstant(zone.zoneId)
        val hourPart = target.format(if (zone.showMinutes) HOUR_MIN_FMT else HOUR_FMT)
        val diffHrs = (target.offset.totalSeconds - state.now.offset.totalSeconds) / 3600.0
        val diffStr = if (diffHrs == diffHrs.toInt().toDouble()) {
            String.format(Locale.ROOT, "%+d", diffHrs.toInt())
        } else {
            String.format(Locale.ROOT, "%+.1f", diffHrs)
        }
        return "$hourPart ${zone.abbreviation}$diffStr"
    }

    private fun formatDate(state: RenderState): String =
        state.now.format(DATE_FMT).uppercase(Locale.ENGLISH)

    companion object {
        private val HOUR_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("H")
        private val HOUR_MIN_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("Hmm")
        private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd")
    }
}
