#!/usr/bin/env bash
# Unicode/accessibility regression on one explicitly selected Android device.
set -euo pipefail
serial="${1:?Usage: scripts/verify_settings_search.sh ADB_SERIAL}"
cd "$(dirname "$0")/.."
./gradlew :testDebugUnitTest :assembleDebug :assembleDebugAndroidTest
adb -s "$serial" install -r build/outputs/apk/debug/LiquidDock-debug.apk
adb -s "$serial" install -r build/outputs/apk/androidTest/debug/LiquidDock-debug-androidTest.apk
result="$(adb -s "$serial" shell am instrument -w \
  com.hellovoid.liquiddock.test/com.hellovoid.liquiddock.SettingsSearchInstrumentation)"
printf '%s\n' "$result"
[[ "$result" == *"PASS Unicode search"* ]]
