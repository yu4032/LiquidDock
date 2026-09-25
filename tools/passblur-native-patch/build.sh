#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PATCH_DIR="$ROOT/tools/passblur-native-patch"
OUT_DIR="${PASSBLUR_PATCH_OUT:-$ROOT/build/passblur-native-patch}"
CLANG_BIN="${CLANG:-clang}"

mkdir -p "$OUT_DIR"

"$CLANG_BIN" --target=aarch64-linux-android \
    -c "$PATCH_DIR/latest_only.S" \
    -o "$OUT_DIR/latest_only.o"

python3 "$PATCH_DIR/extract_text.py" \
    "$OUT_DIR/latest_only.o" \
    "$OUT_DIR/latest_only.bin"

python3 "$PATCH_DIR/verify_blob.py" "$OUT_DIR/latest_only.bin"

if [[ $# -gt 0 ]]; then
    INPUT="$1"
    OUTPUT="${2:-$OUT_DIR/libsurfaceflinger.latest-only.so}"
    python3 "$PATCH_DIR/patch_libsurfaceflinger.py" \
        "$INPUT" "$OUT_DIR/latest_only.bin" "$OUTPUT"
fi
