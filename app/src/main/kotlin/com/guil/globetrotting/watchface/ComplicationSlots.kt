package com.guil.globetrotting.watchface

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import androidx.wear.watchface.CanvasComplication
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.RenderParameters
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.style.CurrentUserStyleRepository
import java.time.ZonedDateTime

/**
 * Complication slot configuration.
 *
 * The watch face's status line shows four fields: `steps · temp · watchBattery · phoneBattery`.
 *   - steps + watchBattery come from on-device providers (real and free)
 *   - temp + phoneBattery come from complications (user-configurable on real watch)
 *
 * Slots are rendered as NO-OP (see NoOpCanvasComplication) — the status line text
 * itself is drawn by `StatusLineLayout` which reads the complication value from
 * the slot's `complicationData.value` and bakes it into the unified text. The
 * slots exist purely so the system editor lets the user pick providers.
 *
 * Bounds are normalised 0..1 over the canvas, positioned roughly where the values
 * appear in the status line. The system editor uses these as tap targets when the
 * user long-presses the face → Customize.
 */
object ComplicationSlots {

    const val WEATHER_SLOT_ID = 1
    const val PHONE_BATTERY_SLOT_ID = 2

    // Status line is at y ≈ 119/480 ≈ 0.248. Tap zones span ~12% width × 8% height
    // each, centred on where the value sits in the unified text. These overlap the
    // text — that's intentional, so the tap target visually covers the value.
    private val WEATHER_BOUNDS = RectF(0.42f, 0.21f, 0.55f, 0.29f)
    private val PHONE_BATTERY_BOUNDS = RectF(0.66f, 0.21f, 0.79f, 0.29f)

    fun build(currentUserStyleRepository: CurrentUserStyleRepository): ComplicationSlotsManager {
        val factory = CanvasComplicationFactory { _, _ -> NoOpCanvasComplication }

        val weather = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = WEATHER_SLOT_ID,
            canvasComplicationFactory = factory,
            supportedTypes = listOf(
                ComplicationType.SHORT_TEXT,
                ComplicationType.RANGED_VALUE,
            ),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = ComplicationSlotBounds(WEATHER_BOUNDS),
        ).build()

        val phoneBattery = ComplicationSlot.createRoundRectComplicationSlotBuilder(
            id = PHONE_BATTERY_SLOT_ID,
            canvasComplicationFactory = factory,
            supportedTypes = listOf(
                ComplicationType.SHORT_TEXT,
                ComplicationType.RANGED_VALUE,
            ),
            defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(),
            bounds = ComplicationSlotBounds(PHONE_BATTERY_BOUNDS),
        ).build()

        return ComplicationSlotsManager(
            listOf(weather, phoneBattery),
            currentUserStyleRepository,
        )
    }
}

/**
 * Renders nothing. We use the complication slot purely as a "data carrier" + tap-target
 * for the system editor, then read its value out and bake it into the status line text
 * ourselves so the visual stays a single unified line of OpenDyslexic text rather than
 * 4 disparate complication chips.
 */
private object NoOpCanvasComplication : CanvasComplication {
    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        renderParameters: RenderParameters,
        slotId: Int,
    ) {
        // No-op by design.
    }

    override fun drawHighlight(
        canvas: Canvas,
        bounds: Rect,
        boundsType: Int,
        zonedDateTime: ZonedDateTime,
        color: Int,
    ) {
        // No-op — system editor draws its own highlights over the slot bounds.
    }

    override fun getData(): ComplicationData =
        androidx.wear.watchface.complications.data.NoDataComplicationData()

    override fun loadData(complicationData: ComplicationData, loadDrawablesAsynchronous: Boolean) {
        // We read the data straight off the slot's StateFlow in the renderer instead.
    }
}
