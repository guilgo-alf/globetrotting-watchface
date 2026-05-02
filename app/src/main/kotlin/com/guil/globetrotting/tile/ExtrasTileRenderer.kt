package com.guil.globetrotting.tile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_MEDIUM
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_NORMAL
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_END
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_START
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ModifiersBuilders.Padding
import androidx.wear.protolayout.ResourceBuilders
import java.nio.ByteBuffer
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders the Timezone Tile (rich version).
 * - Header: current local HH:MM, then `CODE · CITY · DAY DD`
 * - 6 saved zone rows: flag · time · code · 24h band · offset (relative to local)
 * - State B (travelling): home zone dimmed, dashed divider, travel row + caption
 *
 * Bands are 100x9 RGB_565 bitmaps generated at request time and embedded as Tile resources.
 */
object ExtrasTileRenderer {

    // Aligned with watch-face palette (100/80/60/40 ladder on AMOLED black).
    private val WHITE = argb(0xFFEAEAEAL.toInt())
    private val SUBTLE = argb(0xCCEAEAEAL.toInt())
    private val MUTED = argb(0x99EAEAEAL.toInt())
    private val DIM = argb(0x66EAEAEAL.toInt())

    private val HEADER_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val HEADER_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd")
    private val ROW_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    data class RowData(
        val zoneId: ZoneId,
        val abbreviation: String,
        val flag: String,
        val zonedTime: ZonedDateTime,
        val offsetSeconds: Int,
        val highlighted: Boolean,
        val dimmed: Boolean,
        val bandResourceId: String,
        val hideFlag: Boolean = false,
        val hideOffset: Boolean = false,
    )

    data class TileViewModel(
        val headerTime: String,
        val headerSub: String,
        val rows: List<RowData>,
        val travelRow: RowData?,
        val travelCaption: String?,
    )

    fun build(viewModel: TileViewModel): LayoutElement {
        val column = Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setWidth(expand())

        // Header — time only; subtitle suppressed by user preference.
        column.addContent(text(viewModel.headerTime, WHITE, sizeSp = 14, bold = true))
        if (viewModel.headerSub.isNotEmpty()) {
            column.addContent(text(viewModel.headerSub, MUTED, sizeSp = 11))
        }
        column.addContent(spacer(8))

        for (row in viewModel.rows) {
            column.addContent(zoneRow(row))
            column.addContent(spacer(2))
        }

        if (viewModel.travelRow != null) {
            column.addContent(spacer(4))
            column.addContent(dashedDivider())
            column.addContent(spacer(4))
            column.addContent(zoneRow(viewModel.travelRow))
            if (viewModel.travelCaption != null) {
                column.addContent(spacer(2))
                column.addContent(text(viewModel.travelCaption, WHITE, sizeSp = 11, bold = false))
            }
        }

        // Outer box: padding to keep content inside the round canvas safe area.
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setPadding(
                                Padding.Builder()
                                    .setStart(dp(12f))
                                    .setEnd(dp(12f))
                                    .setTop(dp(18f))
                                    .setBottom(dp(18f))
                                    .build(),
                            )
                            .build(),
                    )
                    .addContent(column.build())
                    .build(),
            )
            .build()
    }

    private fun zoneRow(row: RowData): LayoutElement {
        val timeColor = when {
            row.dimmed -> DIM
            else -> WHITE
        }
        val codeColor = when {
            row.dimmed -> DIM
            row.highlighted -> WHITE
            else -> SUBTLE
        }
        val offsetColor = when {
            row.dimmed -> DIM
            row.highlighted -> WHITE
            else -> MUTED
        }

        // Fixed-width slots so left edges of band + offset align across all rows
        // regardless of code length (CEST is 4 chars, others 3).
        // Slots stay fixed-width even when hidden so left edges of band + offset
        // line up consistently across saved rows and the travel row.
        val flagContent = if (row.hideFlag) text("  ", WHITE, sizeSp = 11)
        else text(row.flag.ifEmpty { "  " }, WHITE, sizeSp = 11)
        val offsetContent = if (row.hideOffset) text("", offsetColor, sizeSp = 11)
        else text(formatOffset(row.offsetSeconds), offsetColor, sizeSp = 11)

        return Row.Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setWidth(expand())
            .addContent(fixed(width = 20, content = flagContent))
            .addContent(spacer(3))
            .addContent(fixed(width = 34, content = text(row.zonedTime.format(ROW_TIME_FMT), timeColor, sizeSp = 12, bold = row.highlighted)))
            .addContent(spacer(3))
            .addContent(fixed(width = 32, content = text(row.abbreviation, codeColor, sizeSp = 11, bold = row.highlighted)))
            .addContent(spacer(5))
            .addContent(bandImage(row.bandResourceId))
            .addContent(spacer(5))
            .addContent(fixed(width = 36, content = offsetContent, align = HORIZONTAL_ALIGN_END))
            .build()
    }

    /** Wraps content in a fixed-width Box so column alignment stays consistent across rows. */
    private fun fixed(
        width: Int,
        content: LayoutElement,
        align: Int = HORIZONTAL_ALIGN_START,
    ): Box =
        Box.Builder()
            .setWidth(dp(width.toFloat()))
            .setHorizontalAlignment(align)
            .addContent(content)
            .build()

    private fun bandImage(resourceId: String): Image =
        Image.Builder()
            .setResourceId(resourceId)
            .setWidth(dp(48f))
            .setHeight(dp(8f))
            .build()

    /** 1 dp solid hairline at low opacity — reads cleaner than Unicode dashes at watch sizes. */
    private fun dashedDivider(): LayoutElement =
        Box.Builder()
            .setWidth(dp(140f))
            .setHeight(dp(1f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0x33EAEAEAL.toInt()))
                            .build(),
                    )
                    .build(),
            )
            .build()

    private fun text(
        value: String,
        colour: ColorBuilders.ColorProp,
        sizeSp: Int,
        bold: Boolean = false,
    ): Text {
        val style = FontStyle.Builder()
            .setColor(colour)
            .setSize(sp(sizeSp.toFloat()))
        if (bold) style.setWeight(FONT_WEIGHT_MEDIUM)
        return Text.Builder()
            .setText(value)
            .setFontStyle(style.build())
            .build()
    }

    private fun spacer(sizeDp: Int): Spacer =
        Spacer.Builder()
            .setHeight(dp(sizeDp.toFloat()))
            .setWidth(dp(sizeDp.toFloat()))
            .build()

    /** Format offset like `±0`, `+5:30`, `-7`. */
    fun formatOffset(deltaSeconds: Int): String {
        if (deltaSeconds == 0) return "±0"
        val sign = if (deltaSeconds < 0) "−" else "+"
        val absHours = Math.abs(deltaSeconds) / 3600
        val absMins = (Math.abs(deltaSeconds) % 3600) / 60
        return if (absMins == 0) {
            "$sign$absHours"
        } else {
            String.format(Locale.ROOT, "%s%d:%02d", sign, absHours, absMins)
        }
    }

    fun headerTime(local: ZonedDateTime): String = local.format(HEADER_TIME_FMT)

    fun headerSub(zoneAbbr: String, city: String, local: ZonedDateTime): String {
        val dayDate = local.format(HEADER_DATE_FMT).uppercase(Locale.ENGLISH)
        return "$zoneAbbr · $city · $dayDate"
    }

    fun travelCaption(city: String): String = "📍 you · $city"

    /** Build the Tile resource bundle: one band image per row + travel row if present. */
    fun buildResources(viewModel: TileViewModel, version: String): ResourceBuilders.Resources {
        val builder = ResourceBuilders.Resources.Builder().setVersion(version)
        for (row in viewModel.rows) {
            builder.addIdToImageMapping(row.bandResourceId, bandResource(row.zonedTime, row.highlighted))
        }
        viewModel.travelRow?.let {
            builder.addIdToImageMapping(it.bandResourceId, bandResource(it.zonedTime, highlighted = true))
        }
        return builder.build()
    }

    /** 100×9 RGB_565 band: lifted-grey base, mid-grey 9–17 working window, white now-dot. */
    private fun bandResource(zonedTime: ZonedDateTime, highlighted: Boolean): ResourceBuilders.ImageResource {
        val w = 100
        val h = 9
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val c = Canvas(bm)
        // #2A2A2A so the rail reads as a track on AMOLED black, not as background.
        c.drawColor(Color.parseColor("#2A2A2A"))

        val workColor = if (highlighted) Color.parseColor("#666666") else Color.parseColor("#555555")
        val workPaint = Paint().apply { color = workColor }
        val workStart = (9f / 24f) * w
        val workEnd = (17f / 24f) * w
        c.drawRect(workStart, 0f, workEnd, h.toFloat(), workPaint)

        // End ticks
        val tickPaint = Paint().apply {
            color = Color.parseColor("#444444")
            strokeWidth = 1f
        }
        c.drawLine(0f, 0f, 0f, h.toFloat(), tickPaint)
        c.drawLine(w - 1f, 0f, w - 1f, h.toFloat(), tickPaint)

        // Now dot
        val nowFraction = (zonedTime.hour + zonedTime.minute / 60f) / 24f
        val nowX = nowFraction * w
        val dotRadius = if (highlighted) 5f else 4.5f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawCircle(nowX, h / 2f, dotRadius, dotPaint)

        // RGB_565 = 2 bytes/pixel.
        val bytes = ByteArray(w * h * 2)
        bm.copyPixelsToBuffer(ByteBuffer.wrap(bytes))

        val inline = ResourceBuilders.InlineImageResource.Builder()
            .setData(bytes)
            .setWidthPx(w)
            .setHeightPx(h)
            .setFormat(ResourceBuilders.IMAGE_FORMAT_RGB_565)
            .build()

        return ResourceBuilders.ImageResource.Builder()
            .setInlineResource(inline)
            .build()
    }
}
