#!/usr/bin/env bash
# Capture proof of the installed module plus one repeatable folder open/close.
# Works with Git Bash / MSYS and Linux / macOS; needs adb in PATH.
set -euo pipefail
out="folder-diag-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out"
{
  echo "Host date: $(date -Is)"
  adb devices -l
  echo "Installed LiquidDock package:"
  adb shell pm path com.hellovoid.liquiddock || true
  adb shell dumpsys package com.hellovoid.liquiddock | grep -E 'versionCode=|versionName=|codePath=|firstInstallTime=|lastUpdateTime=' | head -25 || true
  echo "Launcher package:"
  adb shell dumpsys package com.miui.home | grep -E 'versionCode=|versionName=|codePath=' | head -10 || true
  echo "Launcher process PID:"
  adb shell pidof com.miui.home || true
  echo "OS build:"
  adb shell getprop ro.build.version.incremental || true
} > "$out/device.txt" 2>&1
echo "Device details saved: $out/device.txt"
echo "Reopen Launcher after installing APK so LSPosed loads updated code."
echo "Open/close the folder several times, then press Ctrl+C to finish capture."
adb logcat -c
adb logcat -v threadtime -b main -b system -b crash   -s LiquidDock-Diag:I LSPosedFramework:I AndroidRuntime:E libc:F '*:S'   | tee "$out/folder.log"
