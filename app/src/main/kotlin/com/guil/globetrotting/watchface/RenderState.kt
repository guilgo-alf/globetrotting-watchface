package com.guil.globetrotting.watchface

import java.time.ZonedDateTime

data class RenderState(
    val now: ZonedDateTime,
    val watchBatteryPct: Int = 0,
    val phoneBatteryPct: Int? = null,
    val stepCount: Int = 0,
    val weatherTempC: Int? = null,
    val compassBearingDeg: Float? = null,
    val isAmbient: Boolean = false,
)
