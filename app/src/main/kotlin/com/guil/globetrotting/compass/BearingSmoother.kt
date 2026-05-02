package com.guil.globetrotting.compass

class BearingSmoother(private val alpha: Float = 0.10f) {

    private var lastBearing: Float? = null

    fun update(newBearing: Float): Float {
        val last = lastBearing
        if (last == null) {
            lastBearing = newBearing
            return newBearing
        }

        // Shortest angular delta — handles 0/360 wrap.
        var delta = newBearing - last
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f

        val smoothed = (last + alpha * delta + 360f) % 360f
        lastBearing = smoothed
        return smoothed
    }

    fun reset() {
        lastBearing = null
    }
}
