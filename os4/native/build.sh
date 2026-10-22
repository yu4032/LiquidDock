#!/usr/bin/env bash
# Portable out-of-tree build for the read-only OS4 bootstrap.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK" ]]; then
  echo "Set ANDROID_NDK_HOME to an Android NDK installation" >&2
  exit 2
fi
HOST_TAG="linux-x86_64"
if [[ "$(uname -s)" == Darwin ]]; then HOST_TAG="darwin-x86_64"; fi
CLANG="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin/clang"
if [[ ! -x "$CLANG" ]]; then echo "NDK clang missing: $CLANG" >&2; exit 2; fi
mkdir -p "$ROOT/os4/out"
"$CLANG" --target=aarch64-linux-android21 -std=c11 -O2 -fPIC \
  -D_GNU_SOURCE -fvisibility=hidden -Wall -Wextra -Werror \
  -shared -Wl,-z,now -Wl,--no-undefined -Wl,-z,max-page-size=16384 \
  -Wl,--version-script="$ROOT/os4/native/exports.map" \
  "$ROOT/os4/native/native_entry.c" "$ROOT/os4/native/process_gate.c" \
  -llog -o "$ROOT/os4/out/libliquiddock4.so"
echo "Built read-only bootstrap: os4/out/libliquiddock4.so"
