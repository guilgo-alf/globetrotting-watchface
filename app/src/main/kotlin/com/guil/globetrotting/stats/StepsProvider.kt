package com.guil.globetrotting.stats

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class StepsProvider(context: Context) {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    // Persist dayStartCounter across face-switches/reboots so "steps today" is
    // actually steps-since-midnight, not steps-since-this-face-activation.
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val stepsToday = AtomicInteger(0)
    private var dayStartCounter: Int? = null
    private var trackedDate: LocalDate = LocalDate.now()

    init {
        // Restore on construction. If today's date matches the stored date, the
        // dayStartCounter is reused; otherwise the first sensor event for the new
        // day resets it.
        val storedDay = prefs.getString(KEY_DATE, null)?.let(LocalDate::parse)
        val storedStart = if (prefs.contains(KEY_START)) prefs.getInt(KEY_START, 0) else null
        if (storedDay == LocalDate.now() && storedStart != null) {
            trackedDate = storedDay
            dayStartCounter = storedStart
        }
    }

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
            val cumulative = event.values[0].toInt()
            val today = LocalDate.now()
            if (trackedDate != today || dayStartCounter == null) {
                // First event of a new day (or no prior anchor): use the current
                // cumulative count as the new start. Loses steps taken between
                // midnight and this first event — accept that for simplicity.
                trackedDate = today
                dayStartCounter = cumulative
                prefs.edit()
                    .putString(KEY_DATE, today.toString())
                    .putInt(KEY_START, cumulative)
                    .apply()
            }
            stepsToday.set((cumulative - (dayStartCounter ?: cumulative)).coerceAtLeast(0))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sm.unregisterListener(listener)
    }

    fun stepsToday(): Int = stepsToday.get()

    /**
     * True when the device has a step-counter sensor available. False on AVDs and
     * other hardware without one. Used by the renderer to decide whether 0 means
     * "no steps yet" (real sensor) or "no sensor" (use placeholder).
     */
    fun hasSensor(): Boolean = sensor != null

    private companion object {
        const val PREFS_NAME = "steps_provider"
        const val KEY_DATE = "tracked_date"
        const val KEY_START = "day_start_counter"
    }
}
