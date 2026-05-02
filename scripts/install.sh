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
echo "Done. Long-press face on watch -> activate 'Globetrotting Minimalist'"
