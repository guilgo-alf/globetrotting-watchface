package com.guil.globetrotting.watchface

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository

/**
 * Complication slot configuration.
 *
 * Status line shows four fields: `steps · temp · watchBattery · phoneBattery`.
 *   - steps + watchBattery come from on-device providers
 *   - temp + phoneBattery come from complications (user-fillable on real watch)
 *
 * Each slot uses [CanvasComplicationDrawable] (the AOSP-standard implementation
 * that handles the slot's data-loading state machine correctly). The
 * underlying [ComplicationDrawable] is configured transparently — colours all
 * Color.TRANSPARENT — so the library does the data plumbing without painting
 * a visible chip. The renderer reads each slot's `complicationData.value` and
 * bakes it into the unified status line text drawn by StatusLineLayout.
 *
 * History: a previous attempt with a custom NoOp CanvasComplication hung the
 * watch face bind on real Wear OS 5 (10s timeout, error code 4) because it
 * didn't advance the slot's internal data-loading state. CanvasComplicationDrawable
 * does, so the bind completes cleanly.
 */
object ComplicationSlots {

    const val WEATHER_SLOT_ID = 1
    const val PHONE_BATTERY_SLOT_ID = 2

    // Status line is at y ≈ 119/480 ≈ 0.248. Tap zones span ~13% width × 8% height
    // each, centred where the value sits in the unified text. Bounds are tap-targets
    // in the system editor; they don't need to match where the rendered chip would
    // sit because the chip is invisible anyway.
    private val WEATHER_BOUNDS = RectF(0.42f, 0.21f, 0.55f, 0.29f)
    private val PHONE_BATTERY_BOUNDS = RectF(0.66f, 0.21f, 0.79f, 0.29f)

    fun build(
        context: Context,
        currentUserStyleRepository: CurrentUserStyleRepository,
    ): ComplicationSlotsManager {
        val factory = CanvasComplicationFactory { watchState, invalidateCallback ->
            // Transparent drawable: the framework still tracks data state correctly
            // (so the bind completes), but every visible component renders as
            // Color.TRANSPARENT — the library draws nothing in the slot bounds.
            val drawable = ComplicationDrawable(context).apply {
                activeStyle.backgroundColor = Color.TRANSPARENT
                activeStyle.borderColor = Color.TRANSPARENT
                activeStyle.textColor = Color.TRANSPARENT
                activeStyle.titleColor = Color.TRANSPARENT
                activeStyle.iconColor = Color.TRANSPARENT
                activeStyle.rangedValuePrimaryColor = Color.TRANSPARENT
                activeStyle.rangedValueSecondaryColor = Color.TRANSPARENT
                ambientStyle.backgroundColor = Color.TRANSPARENT
                ambientStyle.borderColor = Color.TRANSPARENT
                ambientStyle.textColor = Color.TRANSPARENT
                ambientStyle.titleColor = Color.TRANSPARENT
                ambientStyle.iconColor = Color.TRANSPARENT
                ambientStyle.rangedValuePrimaryColor = Color.TRANSPARENT
                ambientStyle.rangedValueSecondaryColor = Color.TRANSPARENT
            }
            CanvasComplicationDrawable(drawable, watchState, invalidateCallback)
        }

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
