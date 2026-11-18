#!/usr/bin/env bash
# Build OS4 native backend, assemble APK, package libliquiddock4.so, 16K-align, sign, and verify.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VARIANT="${1:-debug}"
OUT_DIR="$ROOT/build/outputs/os4"
mkdir -p "$OUT_DIR"

BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-}"
if [[ -z "$BUILD_TOOLS" ]]; then
  BUILD_TOOLS="$(ls -1d /mnt/extra/Android/build-tools/* 2>/dev/null | sort -V | tail -n 1 || true)"
fi
if [[ -z "$BUILD_TOOLS" && -n "${ANDROID_HOME:-}" ]]; then
  BUILD_TOOLS="$(ls -1d "$ANDROID_HOME/build-tools/"* 2>/dev/null | sort -V | tail -n 1 || true)"
fi
if [[ -z "$BUILD_TOOLS" || ! -x "$BUILD_TOOLS/zipalign" || ! -x "$BUILD_TOOLS/apksigner" ]]; then
  echo "zipalign/apksigner not found; set ANDROID_BUILD_TOOLS" >&2
  exit 2
fi

cd "$ROOT"
bash os4/native/build.sh

if [[ "$VARIANT" == "release" ]]; then
  ./gradlew testDebugUnitTest assembleRelease --quiet
  UNSIGNED_APK="$ROOT/build/outputs/apk/release/LiquidDock-release-unsigned.apk"
  if [[ -f "$HOME/.secure/liquiddock/keys" ]]; then
    # shellcheck disable=SC1091
    source "$HOME/.secure/liquiddock/keys"
  fi
  KS="${LIQUIDDOCK_KEYSTORE:?Set LIQUIDDOCK_KEYSTORE for release signing}"
  ALIAS="${LIQUIDDOCK_KEY_ALIAS:?Set LIQUIDDOCK_KEY_ALIAS}"
  SPASS="${LIQUIDDOCK_STORE_PASS:?Set LIQUIDDOCK_STORE_PASS}"
  KPASS="${LIQUIDDOCK_KEY_PASS:-$SPASS}"
  FINAL_APK="$OUT_DIR/LiquidDock-release-signed.apk"
elif [[ "$VARIANT" == "debug" ]]; then
  ./gradlew testDebugUnitTest assembleDebug --quiet
  UNSIGNED_APK="$ROOT/build/outputs/apk/debug/LiquidDock-debug.apk"
  KS="$HOME/.android/debug.keystore"
  ALIAS="androiddebugkey"
  SPASS="android"
  KPASS="android"
  FINAL_APK="$OUT_DIR/LiquidDock-debug-signed.apk"
else
  echo "Usage: $0 [debug|release]" >&2
  exit 2
fi

PACKED_APK="$OUT_DIR/.packed-$VARIANT.apk"
ALIGNED_APK="$OUT_DIR/.aligned-$VARIANT.apk"

python3 os4/tools/package_native.py "$UNSIGNED_APK" os4/out/libliquiddock4.so "$PACKED_APK"
"$BUILD_TOOLS/zipalign" -P 16 -f 4 "$PACKED_APK" "$ALIGNED_APK"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KS" \
  --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$SPASS" \
  --key-pass "pass:$KPASS" \
  --alignment-preserved \
  --out "$FINAL_APK" \
  "$ALIGNED_APK"
rm -f "$PACKED_APK" "$ALIGNED_APK"

python3 os4/tools/verify_installer.py "$FINAL_APK" os4/out/libliquiddock4.so
echo "Ready to install: $FINAL_APK"
