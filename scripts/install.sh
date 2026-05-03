#!/bin/bash
set -e

APK="app/build/outputs/apk/release/app-release.apk"
ADB="${ADB:-adb}"

if ! command -v "$ADB" >/dev/null 2>&1; then
    if [ -x "/c/Users/Guilherme/AppData/Local/Android/Sdk/platform-tools/adb.exe" ]; then
        ADB="/c/Users/Guilherme/AppData/Local/Android/Sdk/platform-tools/adb.exe"
    fi
fi

if [ ! -f "$APK" ]; then
    echo "APK not found at $APK. Run ./gradlew assembleRelease first." >&2
    exit 1
fi

DEVICES=$("$ADB" devices | grep -v "List of" | grep -c "device$" || true)
if [ "$DEVICES" -eq 0 ]; then
    echo "No ADB device. Pair the watch first:" >&2
    echo "  $ADB pair <watch-ip>:<port>" >&2
    echo "  $ADB connect <watch-ip>:5555" >&2
    exit 1
fi

echo "Installing $APK..."
"$ADB" install -r "$APK"

# Grant ACTIVITY_RECOGNITION so the step counter actually receives events on
# Android 10+. Without this, StepsProvider.stepsToday() returns 0 forever on
# real hardware. Idempotent — safe to re-run.
echo "Granting ACTIVITY_RECOGNITION (required for step counter)..."
"$ADB" shell pm grant com.guil.globetrotting android.permission.ACTIVITY_RECOGNITION || \
    echo "(grant returned non-zero — likely already granted; continuing)"

echo "Done. Long-press face on watch -> activate 'Globetrotting Minimalist'"
