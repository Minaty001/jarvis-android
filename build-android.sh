#!/bin/bash
# JARVIS AI Android Build Script
set -e

echo "=== JARVIS AI Android Build ==="
echo ""

# Check prerequisites
echo "Checking prerequisites..."
command -v java >/dev/null 2>&1 || { echo "Java JDK required."; exit 1; }

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
if [ ! -d "$ANDROID_HOME" ]; then
    echo "Android SDK not found at $ANDROID_HOME"
    echo "Set ANDROID_HOME or install Android SDK"
    exit 1
fi

echo "Android SDK: $ANDROID_HOME"
echo ""

# Navigate to android directory
cd android

echo "Cleaning previous build..."
./gradlew clean

echo "Compiling Kotlin..."
./gradlew compileDebugKotlin

echo "Building debug APK..."
./gradlew assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo ""
    echo "=== Build Complete ==="
    echo "APK: $(pwd)/$APK_PATH"
    echo "Size: $(du -h "$APK_PATH" | cut -f1)"
    echo ""
    echo "Install on device:"
    echo "  adb install $APK_PATH"
else
    echo "Build failed. Check output above."
    exit 1
fi
