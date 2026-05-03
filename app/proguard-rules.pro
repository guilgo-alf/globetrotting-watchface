# ProGuard rules. Minify is currently OFF in release for the sideload-only build
# (see app/build.gradle.kts -> buildTypes.release.isMinifyEnabled).
#
# These rules are PRE-LOADED so that the moment minify ever flips on, the build
# doesn't break in non-obvious ways. If you turn minify on:
#   1. Verify the watch face still loads (long-press picker -> tap)
#   2. Verify the tile renders (swipe right from the watch face)
#   3. Verify the tap-to-open ZonesActivity launches with rotary scroll
#   4. Watch logcat for ClassNotFoundException / NoSuchMethodError on first run

# --- Watch face binders -------------------------------------------------------
-keep class androidx.wear.watchface.** { *; }
-keep class androidx.wear.watchface.style.** { *; }
-keep class androidx.wear.watchface.complications.** { *; }
-keep class * extends androidx.wear.watchface.WatchFaceService { *; }
-keep class * extends androidx.wear.watchface.Renderer { *; }

# --- Tile binders -------------------------------------------------------------
-keep class androidx.wear.tiles.** { *; }
-keep class androidx.wear.protolayout.** { *; }
-keep class * extends androidx.wear.tiles.TileService { *; }

# --- Compose runtime ----------------------------------------------------------
# Compose generates code that the runtime looks up via reflection / metadata.
-keep class androidx.compose.runtime.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-keep class androidx.compose.ui.tooling.** { *; }

# --- Wear Compose -------------------------------------------------------------
# ScalingLazyColumn and rotaryScrollable use reflective metadata lookups.
-keep class androidx.wear.compose.foundation.** { *; }
-keep class androidx.wear.compose.material.** { *; }

# --- Our entry points (services + activities) ---------------------------------
-keep class com.guil.globetrotting.watchface.GlobetrottingWatchFaceService { *; }
-keep class com.guil.globetrotting.tile.ExtrasTileService { *; }
-keep class com.guil.globetrotting.zones.ZonesActivity { *; }

# --- Sensor lifecycle ---------------------------------------------------------
-keepclassmembers class * implements android.hardware.SensorEventListener {
    public void onSensorChanged(android.hardware.SensorEvent);
    public void onAccuracyChanged(android.hardware.Sensor, int);
}

# --- BroadcastReceiver (used by ExtrasTileService for TIMEZONE_CHANGED) -------
-keep class * extends android.content.BroadcastReceiver { *; }
