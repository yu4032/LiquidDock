#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PATCH_DIR="$ROOT/tools/passblur-native-patch"
OUT_DIR="${PASSBLUR_PATCH_OUT:-$ROOT/build/passblur-native-patch}"

CLANG_BIN="${CLANG:-clang}"
LLD_BIN="${LD_LLD:-ld.lld}"
OBJCOPY_BIN="${LLVM_OBJCOPY:-}"

if [[ -z "$OBJCOPY_BIN" ]]; then
    if command -v llvm-objcopy >/dev/null 2>&1; then
        OBJCOPY_BIN=llvm-objcopy
    else
        OBJCOPY_BIN=objcopy
    fi
fi

mkdir -p "$OUT_DIR"

"$CLANG_BIN" --target=aarch64-linux-android \
    -c "$PATCH_DIR/latest_only.S" \
    -o "$OUT_DIR/latest_only.o"

"$LLD_BIN" -nostdlib \
    -T "$PATCH_DIR/latest_only.ld" \
    -o "$OUT_DIR/latest_only.elf" \
    "$OUT_DIR/latest_only.o"

"$OBJCOPY_BIN" -O binary --only-section=.text \
    "$OUT_DIR/latest_only.elf" \
    "$OUT_DIR/latest_only.bin"

python3 "$PATCH_DIR/verify_blob.py" "$OUT_DIR/latest_only.bin"

if [[ $# -gt 0 ]]; then
    INPUT="$1"
    OUTPUT="${2:-$OUT_DIR/libsurfaceflinger.latest-only.so}"
    python3 "$PATCH_DIR/patch_libsurfaceflinger.py" \
        "$INPUT" "$OUT_DIR/latest_only.bin" "$OUTPUT"
fi
