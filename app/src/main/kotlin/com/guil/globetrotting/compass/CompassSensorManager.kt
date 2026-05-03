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
            // Snapshot the listener locally so a concurrent stop() can null it
            // without racing against the dispatch below — if it's already null
            // we exit early instead of calling a stale callback.
            val listener = bearingListener ?: return

            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            // orientation[0] = azimuth in radians (-PI..PI, 0 = north)
            val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            // Guard against degenerate sensor data (e.g. emulator with no magnetic
            // field set produces NaN). Skip the update so currentBearing keeps its
            // last good value rather than becoming NaN and hiding the arc.
            if (azimuthDeg.isNaN() || azimuthDeg.isInfinite()) return
            val normalised = (azimuthDeg + 360f) % 360f
            listener.invoke(smoother.update(normalised))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(onBearingUpdate: (Float) -> Unit) {
        if (rotationSensor == null) return
        bearingListener = onBearingUpdate
        // SENSOR_DELAY_NORMAL ≈ 200 ms / 5 Hz — slower polling kills high-frequency
        // jitter at the source. Combined with the bearing smoother and the invalidate
        // throttle it gives a calm, intentional rotation.
        sensorManager.registerListener(
            sensorListener,
            rotationSensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        )
    }

    fun stop() {
        // Null the listener BEFORE unregistering so any in-flight sensor callback
        // on the sensor thread sees null and returns early (see snapshot in
        // onSensorChanged). Then unregister, which is idempotent.
        bearingListener = null
        sensorManager.unregisterListener(sensorListener)
        smoother.reset()
    }
}
