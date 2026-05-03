package com.guil.globetrotting.watchface.data

import android.graphics.Color

object WatchFaceColors {
    const val BACKGROUND = Color.BLACK
    // Off-white at 92% luminance — pure #FFFFFF on AMOLED creates aggressive halation
    // that amplifies OpenDyslexic's chunky perception. #EAEAEA reads as "crafted typography".
    val FOREGROUND = Color.rgb(0xEA, 0xEA, 0xEA)
    val SUBTLE  = Color.argb(0xCC, 0xEA, 0xEA, 0xEA)
    val MUTED   = Color.argb(0x99, 0xEA, 0xEA, 0xEA)
    val FAINT   = Color.argb(0x66, 0xEA, 0xEA, 0xEA)
    const val ACCENT = 0xFFFFAA00.toInt()
    // Slightly desaturated red — reads as "red" on AMOLED black without
    // the chromatic fringing pure #FF0000 produces at small sizes.
    const val NORTH_RED = 0xFFE63946.toInt()

    val FOREGROUND_AMBIENT = Color.argb(0x80, 0xEA, 0xEA, 0xEA)
}

object WatchFaceMetrics {
    const val CANVAS = 480
    const val CENTRE = 240f

    const val BIG_TIME_SIZE_PX = 162f
    const val BIG_TIME_TRACKING = -13f
    const val BIG_TIME_SCALE_X = 0.96f
    const val SECONDS_SUPER_SIZE_PX = 24f
    const val SECONDS_GAP_PX = 8f
    const val STATUS_LINE_SIZE_PX = 21f
    const val TIMEZONE_ROW_SIZE_PX = 18f
    const val DATE_LABEL_SIZE_PX = 24f

    const val BIG_TIME_CENTRE_X = 238f
    const val BIG_TIME_CENTRE_Y = 221f
    const val STATUS_LINE_CENTRE_X = 246f
    const val STATUS_LINE_CENTRE_Y = 119f
}
