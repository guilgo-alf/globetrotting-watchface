package com.guil.globetrotting.stats

import android.content.Context
import android.os.BatteryManager

class BatteryProvider(context: Context) {
    private val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    fun currentPercent(): Int =
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
}
