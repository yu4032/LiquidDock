#!/usr/bin/env bash
# Unicode/accessibility regression on one explicitly selected Android device.
set -euo pipefail
serial="${1:?Usage: scripts/verify_settings_search.sh ADB_SERIAL}"
cd "$(dirname "$0")/.."
./gradlew :testDebugUnitTest :assembleDebug :assembleDebugAndroidTest

# The Gradle APK contains the Xposed entrypoint metadata, but the OS4 native
# backend is built and packaged separately. Installing the raw Gradle APK makes
# LSPosed load native_init.list and then fail to find libliquiddock4.so, leaving
# every OS4 feature detached after the next Launcher restart.
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_root" ]]; then
  echo "Set ANDROID_HOME or ANDROID_SDK_ROOT to install the device-test APK" >&2
  exit 2
fi
build_tools="$(find "$sdk_root/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
if [[ -z "$build_tools" || ! -x "$build_tools/zipalign" || ! -x "$build_tools/apksigner" ]]; then
  echo "Android build-tools with zipalign and apksigner are required" >&2
  exit 2
fi

bash os4/native/build.sh
native_tmp="$(mktemp -d)"
trap 'rm -rf "$native_tmp"' EXIT
unsigned_apk="$native_tmp/LiquidDock-debug-native-unsigned.apk"
aligned_apk="$native_tmp/LiquidDock-debug-native-aligned.apk"
native_apk="$native_tmp/LiquidDock-debug-native.apk"

source ~/miniconda3/bin/activate base && python os4/tools/package_native.py \
  build/outputs/apk/debug/LiquidDock-debug.apk \
  os4/out/libliquiddock4.so \
  "$unsigned_apk"
"$build_tools/zipalign" -P 16 -f 4 "$unsigned_apk" "$aligned_apk"
"$build_tools/apksigner" sign \
  --alignment-preserved \
  --ks signing/hellovoid-debug.keystore \
  --ks-key-alias hellovoid-debug \
  --ks-pass pass:hellovoid-debug \
  --key-pass pass:hellovoid-debug \
  --out "$native_apk" \
  "$aligned_apk"

source ~/miniconda3/bin/activate base && python os4/tools/verify_installer.py \
  "$native_apk" \
  os4/out/libliquiddock4.so
if ! unzip -p "$native_apk" META-INF/xposed/scope.list | grep -Fxq "system"; then
  echo "Packaged APK is missing the explicit system_server scope" >&2
  exit 1
fi
"$build_tools/apksigner" verify --verbose --print-certs "$native_apk"
# LSPosed's system_server callback runs before app processes. Incremental/streamed installs can
# leave the registered APK path unavailable during that early boot window, so materialize both
# APKs before asking PackageManager to install them.
adb -s "$serial" install -r --no-incremental --no-streaming "$native_apk"
adb -s "$serial" install -r --no-incremental --no-streaming \
  build/outputs/apk/androidTest/debug/LiquidDock-debug-androidTest.apk
# UI Automation cannot see accessibility nodes while the display is asleep. Wake only when needed
# so the regression remains repeatable on devices whose screen timed out during the build.
wake_state="$(adb -s "$serial" shell dumpsys power | rg -o 'mWakefulness=[A-Za-z]+' | head -n 1)"
if [[ "$wake_state" != "mWakefulness=Awake" ]]; then
  adb -s "$serial" shell input keyevent KEYCODE_POWER
  for attempt in {1..8}; do
    wake_state="$(adb -s "$serial" shell dumpsys power | rg -o 'mWakefulness=[A-Za-z]+' | head -n 1)"
    [[ "$wake_state" == "mWakefulness=Awake" ]] && break
    sleep 0.25
  done
fi
if [[ "$wake_state" != "mWakefulness=Awake" ]]; then
  echo "Device did not wake for the accessibility regression" >&2
  exit 2
fi
adb -s "$serial" shell wm dismiss-keyguard
result="$(adb -s "$serial" shell am instrument -w \
  com.hellovoid.liquiddock.test/com.hellovoid.liquiddock.SettingsSearchInstrumentation)"
printf '%s\n' "$result"
[[ "$result" == *"PASS Unicode search"* ]]
