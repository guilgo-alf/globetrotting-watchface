package com.guil.globetrotting.stats

import android.content.Context
import android.os.BatteryManager

class BatteryProvider(context: Context) {
    private val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    /**
     * Battery percent in 0..100, or 0 if the property hasn't been populated yet.
     * `getIntProperty` can return `Integer.MIN_VALUE` on first-boot or for sensors
     * that don't expose capacity — `coerceIn` clamps that to 0 so we never display
     * a nonsense negative or out-of-range value, even for one frame.
     */
    fun currentPercent(): Int {
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        // Sentinel: BATTERY_PROPERTY_CAPACITY can return Integer.MIN_VALUE if the
        // underlying property isn't ready. Treat that as 0 rather than letting the
        // clamp produce a misleading "0%" when the real value is "unknown".
        if (raw == Integer.MIN_VALUE) return 0
        return raw.coerceIn(0, 100)
    }
}
