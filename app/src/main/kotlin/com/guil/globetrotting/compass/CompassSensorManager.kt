package com.guil.globetrotting.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class CompassSensorManager(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val smoother = BearingSmoother()
    private var bearingListener: ((Float) -> Unit)? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            // orientation[0] = azimuth in radians (-PI..PI, 0 = north)
            val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            val normalised = (azimuthDeg + 360f) % 360f
            bearingListener?.invoke(smoother.update(normalised))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(onBearingUpdate: (Float) -> Unit) {
        if (rotationSensor == null) return
        bearingListener = onBearingUpdate
        sensorManager.registerListener(
            sensorListener,
            rotationSensor,
            SensorManager.SENSOR_DELAY_UI,
        )
    }

    fun stop() {
        sensorManager.unregisterListener(sensorListener)
        bearingListener = null
        smoother.reset()
    }
}
