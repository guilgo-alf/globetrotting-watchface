#!/bin/bash
set -e
KEYSTORE="$HOME/.android/globetrotting-debug.keystore"
KEYTOOL="${KEYTOOL:-keytool}"

# Fall back to Android Studio's bundled JBR keytool when not on PATH.
if ! command -v "$KEYTOOL" >/dev/null 2>&1; then
    if [ -x "/c/Program Files/Android/Android Studio/jbr/bin/keytool.exe" ]; then
        KEYTOOL="/c/Program Files/Android/Android Studio/jbr/bin/keytool.exe"
    fi
fi

if [ -f "$KEYSTORE" ]; then
    echo "Keystore already exists at $KEYSTORE"
    exit 0
fi

mkdir -p "$HOME/.android"
"$KEYTOOL" -genkey -v \
    -keystore "$KEYSTORE" \
    -alias globetrotting \
    -keyalg RSA -keysize 2048 \
    -validity 10000 \
    -storepass globetrottingdev \
    -keypass globetrottingdev \
    -dname "CN=Guilherme Watch Face, O=Personal, C=NL"

echo "Keystore created at $KEYSTORE"
