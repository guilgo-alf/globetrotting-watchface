package com.guil.globetrotting.tile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.wear.protolayout.ActionBuilders
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
    // Used for offsets and other "quiet" tile chrome — same grey as the band rail.
    private val DIM_RAIL = argb(0xFF303030L.toInt())
    // For the minutes that echo the home-zone minute: pure black on AMOLED black
    // makes them invisible. The hour stays, the redundant minutes vanish.
    private val INVISIBLE = argb(0xFF000000L.toInt())

    // Pill background tint and the linked corner-fill colour for highlighted bands.
    // RGB_565 has no alpha channel, so the band bitmap can't blend transparently into
    // the pill — instead we paint its rounded-clip corners with the colour the pill
    // tint produces when composited over black. If you change PILL_TINT_ARGB, you MUST
    // update PILL_TINT_OVER_BLACK_RGB. Composite formula (per channel):
    //   r = (a/255) * tint_r + (1 - a/255) * 0 = (0x22/255) * 0xEA ≈ 0x1F
    // 0x1C is rounded down a hair so the corner doesn't appear lighter than the pill.
    private const val PILL_TINT_ARGB = 0x22EAEAEAL
    private const val PILL_TINT_OVER_BLACK_RGB = "#1C1C1C"

    private val HEADER_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val HEADER_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd")
    private val ROW_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val HH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH")
    private val COLON_MM_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern(":mm")

    /** Uniform inter-column gap inside a row. */
    private const val COL_GAP = 6

    /**
     * Total dp width for every row. Equal to:
     *   lead 8 + code 30 + COL_GAP + flag 16 + COL_GAP + time 34 + COL_GAP + band 38
     *   + COL_GAP + offset 36 = 186 dp.
     * Highlighted rows have shorter inner content but their outer Row keeps this width
     * so flag/time/band columns line up across all rows.
     */
    private const val ROW_FIXED_WIDTH_DP = 186

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
        // When true, the :mm portion of the time renders in a dimmer colour. Used to
        // reduce visual repetition for rows that share the same minute as the home
        // (CET/CEST) zone — the only meaningful info is the hour, the minutes echo.
        val dimMinutes: Boolean = false,
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

        // Header suppressed by user preference — tile opens straight into the rows.
        if (viewModel.headerTime.isNotEmpty()) {
            column.addContent(text(viewModel.headerTime, WHITE, sizeSp = 14, bold = true))
        }
        if (viewModel.headerSub.isNotEmpty()) {
            column.addContent(text(viewModel.headerSub, MUTED, sizeSp = 11))
        }

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
        // Whole-tile Clickable launches ZonesActivity for the full scrollable list.
        // Tile swipes still work because the tile system intercepts horizontal swipe
        // gestures before they reach this Clickable — only taps land here.
        val openZonesClickable = ModifiersBuilders.Clickable.Builder()
            .setId("open_zones")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName("com.guil.globetrotting")
                            .setClassName("com.guil.globetrotting.zones.ZonesActivity")
                            .build(),
                    )
                    .build(),
            )
            .build()

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(openZonesClickable)
                    .build(),
            )
            .addContent(
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setPadding(
                                // Asymmetric padding pushes content rightward so flag column
                                // clears the round bezel curve on the left edge.
                                Padding.Builder()
                                    .setStart(dp(18f))
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
            // Same rail-grey as the band off-hours and the dimmed :mm — keeps the
            // tile to a single "quiet info" grey across all surfaces.
            else -> DIM_RAIL
        }

        // Fixed-width slots so left edges of band + offset align across all rows
        // regardless of code length (CEST is 4 chars, others 3).
        // Slots stay fixed-width even when hidden so left edges of band + offset
        // line up consistently across saved rows and the travel row.
        val flagContent = if (row.hideFlag) text("  ", WHITE, sizeSp = 11)
        else text(row.flag.ifEmpty { "  " }, WHITE, sizeSp = 11)
        val offsetContent = if (row.hideOffset) text("", offsetColor, sizeSp = 11)
        else text(formatOffset(row.offsetSeconds), offsetColor, sizeSp = 11)

        // Build the inner content Row (code · flag · time · band [· offset]).
        val innerContent = Row.Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setWidth(DimensionBuilders.wrap())
            // 8 dp lead so the highlight pill's rounded corner clears the code text.
            .addContent(spacer(8))
            .addContent(fixed(width = 30, content = text(row.abbreviation, codeColor, sizeSp = 11, bold = row.highlighted), align = HORIZONTAL_ALIGN_END))
            .addContent(spacer(COL_GAP))
            // Flag slot 14 dp — 2 dp tighter than emoji's natural box, trims padding.
            .addContent(fixed(width = 14, content = flagContent))
            // Flag→time gap collapsed — no spacer between flag and the time column.
            .addContent(fixed(width = 34, content = timeContent(row, timeColor), align = HORIZONTAL_ALIGN_END))
            // Time→band gap = COL_GAP + 3 (9 dp).
            .addContent(spacer(COL_GAP + 3))
            .addContent(bandImage(row.bandResourceId))
            .apply {
                if (row.highlighted) {
                    // Trailing pad inside the pill — keeps the band a hair from the rounded edge.
                    addContent(spacer(10))
                } else {
                    addContent(spacer(COL_GAP))
                    addContent(fixed(width = 36, content = offsetContent, align = HORIZONTAL_ALIGN_START))
                }
            }
            .build()

        val styledContent: LayoutElement = if (row.highlighted) {
            // Pill wraps just the inner content so its right edge sits next to the band,
            // not at the row's right edge.
            Box.Builder()
                .setWidth(DimensionBuilders.wrap())
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(argb(PILL_TINT_ARGB.toInt()))
                                .setCorner(
                                    ModifiersBuilders.Corner.Builder()
                                        .setRadius(dp(12f))
                                        .build(),
                                )
                                .build(),
                        )
                        .setPadding(
                            Padding.Builder()
                                .setTop(dp(4f))
                                .setBottom(dp(4f))
                                .build(),
                        )
                        .build(),
                )
                .addContent(innerContent)
                .build()
        } else {
            innerContent
        }

        // Outer Row at fixed full-row width — keeps flag column aligned across all rows
        // regardless of whether the row is highlighted or not.
        return Row.Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setWidth(dp(ROW_FIXED_WIDTH_DP.toFloat()))
            .addContent(styledContent)
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

    /**
     * Renders the row's time as "Hmm" — no leading zero on the hour, no separator
     * between hour and minutes. When `dimMinutes` is set, the "mm" portion renders
     * in the rail-grey colour but the digits are flush against the hour.
     */
    private fun timeContent(row: RowData, primary: ColorBuilders.ColorProp): LayoutElement {
        val hourStr = row.zonedTime.hour.toString()
        val mmStr = String.format(java.util.Locale.ROOT, "%02d", row.zonedTime.minute)
        // Highlighted row always renders the full time in primary — skipping the
        // dim-minutes rule because the highlighted row trivially matches the home
        // minute (it IS the home zone in many cases) and we don't want to hide its
        // minutes for that reason.
        if (!row.dimMinutes || row.highlighted) {
            return text("$hourStr$mmStr", primary, sizeSp = 12, bold = row.highlighted)
        }
        return Row.Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(text(hourStr, primary, sizeSp = 12, bold = row.highlighted))
            .addContent(text(mmStr, INVISIBLE, sizeSp = 12, bold = row.highlighted))
            .build()
    }

    private fun bandImage(resourceId: String): Image =
        Image.Builder()
            .setResourceId(resourceId)
            .setWidth(dp(38f))
            // 10 dp tall — gives the working-hours block enough vertical mass to feel
            // like a "block" rather than a "line", and lets the now-dot have breathing room.
            .setHeight(dp(10f))
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

    /**
     * Band visualisation — the anchor of the tile design.
     *
     *   • The pill = a single day in the zone's local time (0 → 24 h, left → right)
     *   • The warm-amber segment = 9-17 working hours — the SEMANTIC anchor
     *     ("daylight"), not just a luminance jump from the rail
     *   • The white dot = current local time in that zone, accent reserved exclusively
     *     for "now" so it always wins the eye
     *
     * Palette derived from agent audit:
     *   - Rail #2A2A2A — bucket-centred grey on RGB_565 (no green tint), clearly
     *     visible vs AMOLED black so the day-container reads
     *   - Working window #E8C77A standard — warm amber carries "daylight" semantics
     *     beyond luminance, so the colour difference survives the 11-px rendering scale
     *   - Working window #FFD78A highlighted — same hue, brighter chroma; tells the
     *     user "this is your zone" without an unrelated colour leap
     *   - Now-dot #FFFFFF + 1 px black stroke — the only white element, always pops
     */
    private fun bandResource(zonedTime: ZonedDateTime, highlighted: Boolean): ResourceBuilders.ImageResource {
        val w = 100
        val h = 12
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val c = Canvas(bm)
        // RGB_565 has no alpha — the corners outside the rounded clip can't be
        // transparent. Paint them the colour we EXPECT to be behind the band, so the
        // rounded clip blends invisibly into the row background. See the constant
        // PILL_TINT_OVER_BLACK_RGB at the top — those two values are paired.
        val cornerFill = if (highlighted) Color.parseColor(PILL_TINT_OVER_BLACK_RGB)
        else Color.BLACK
        c.drawColor(cornerFill)

        // Clip outer pill — corners outside fall through to the black bg, so they
        // visually disappear on the watch's AMOLED background.
        val outerCorner = 4f
        c.save()
        c.clipPath(android.graphics.Path().apply {
            addRoundRect(
                0f, 0f, w.toFloat(), h.toFloat(),
                outerCorner, outerCorner,
                android.graphics.Path.Direction.CW,
            )
        })

        // Rail.
        c.drawColor(Color.parseColor("#2A2A2A"))

        // Working-hours block — warm amber, rounded inner corners, slight inset top/bottom
        // so it sits inside the rail rather than overflowing the pill at the edges.
        val workColor = if (highlighted) Color.parseColor("#FFD78A") else Color.parseColor("#E8C77A")
        val workPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = workColor }
        val workStart = (9f / 24f) * w
        val workEnd = (17f / 24f) * w
        val innerCorner = 2f
        c.drawRoundRect(
            workStart, 0.5f, workEnd, h - 0.5f,
            innerCorner, innerCorner, workPaint,
        )

        // Now-dot — colour signals work mode:
        //   • Black dot from exactly 09:00 → 16:30 (the "deep work" window)
        //   • White dot otherwise, including the last 30 min of the working block —
        //     wind-down phase reads as "available again" before the amber bar ends.
        // Asymmetric on purpose: no pre-emptive flip at the start of the day, but
        // an early flip on the way out so the wind-down is visible.
        val nowFraction = (zonedTime.hour + zonedTime.minute / 60f) / 24f
        val nowX = nowFraction * w
        val dotRadius = 4f
        val deepWorkStart = 9f / 24f          // 09:00 sharp
        val deepWorkEnd = 16.5f / 24f          // 16:30 (30 min before amber bar ends)
        val isDeepWork = nowFraction in deepWorkStart..deepWorkEnd
        val (dotFillColor, dotStrokeColor) = if (isDeepWork) {
            // Black on yellow — strongest possible contrast, reads as a deliberate
            // marker rather than a status indicator.
            Color.BLACK to Color.BLACK
        } else {
            Color.WHITE to Color.BLACK
        }
        val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotFillColor }
        val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dotStrokeColor
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        c.drawCircle(nowX, h / 2f, dotRadius, dotFill)
        c.drawCircle(nowX, h / 2f, dotRadius, dotStroke)

        c.restore()

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
