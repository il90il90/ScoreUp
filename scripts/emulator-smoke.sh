#!/usr/bin/env bash
# Installs a (minified) release APK on the running emulator, launches it and fails on any crash.
# Usage: scripts/emulator-smoke.sh <apk> <component>
set -euo pipefail
apk="$1"; comp="$2"; pkg="${comp%%/*}"
adb uninstall "$pkg" >/dev/null 2>&1 || true
adb install -r "$apk"
adb logcat -c
adb shell am start -W -n "$comp"
sleep 8
# Exercise a few taps so the scoring + persistence + serialization paths run in the R8 build.
adb shell input tap 300 500; adb shell input tap 300 1400; adb shell input keyevent KEYCODE_BACK || true
adb shell am start -W -n "$comp"; sleep 4
if adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime: Process: $pkg"; then
  echo "::error::$pkg crashed"; adb logcat -d | grep -A40 "FATAL EXCEPTION" | head -80; exit 1
fi
adb shell pidof "$pkg" > /dev/null || { echo "::error::$pkg is not running"; adb logcat -d | tail -100; exit 1; }
echo "smoke OK: $pkg"
