package com.guil.globetrotting.stats

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class StepsProvider(context: Context) {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val stepsToday = AtomicInteger(0)

    // TYPE_STEP_COUNTER returns total steps since last device reboot. We snapshot the
    // counter at first event of each day and subtract; gives us "steps today" without
    // persistence. Acceptable for a personal face — restart on reboot is fine.
    private var dayStartCounter: Int? = null
    private var trackedDate: LocalDate = LocalDate.now()

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
            val cumulative = event.values[0].toInt()
            val today = LocalDate.now()
            if (trackedDate != today || dayStartCounter == null) {
                trackedDate = today
                dayStartCounter = cumulative
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
}
