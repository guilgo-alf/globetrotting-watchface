package com.guil.globetrotting.watchface

import java.time.ZonedDateTime

data class RenderState(
    val now: ZonedDateTime,
    val watchBatteryPct: Int = 0,
    val stepCount: Int = 0,
    // Phone battery + weather temperature: on the emulator these are populated with
    // believable placeholders so the design reads as intended. On the real Galaxy
    // Watch 6 Classic these need to come from:
    //   - phoneBatteryPct: Wear DataLayer / MessageClient asking the paired phone
    //     for its BatteryManager.BATTERY_PROPERTY_CAPACITY
    //   - weatherTempC: a Complication slot wired to a weather data source
    // Both are nullable so the layout can render "--" if the source ever fails.
    val phoneBatteryPct: Int? = null,
    val weatherTempC: Int? = null,
    val compassBearingDeg: Float? = null,
    val isAmbient: Boolean = false,
)
