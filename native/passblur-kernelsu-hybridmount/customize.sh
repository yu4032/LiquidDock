#!/system/bin/sh
# KernelSU sources this file after extracting the ZIP. Hybrid Mount owns mounts.
[ "$KSU" = true ] || abort "KernelSU is required"
[ "$ARCH" = arm64 ] || abort "AArch64 is required"

HM=/data/adb/modules/hybrid_mount
[ -f "$HM/module.prop" ] && [ ! -e "$HM/disable" ] || abort "Enable Hybrid Mount first"
grep -qx 'id=hybrid_mount' "$HM/module.prop" || abort "Unexpected metamodule"
grep -qx 'metamodule=1' "$HM/module.prop" || abort "Hybrid Mount is not a metamodule"

STATE=/data/adb/hybrid-mount/run/state.json
if [ -f "$STATE" ] && grep -Eq '"failed_stage"[[:space:]]*:[[:space:]]*"' "$STATE"; then
  abort "Hybrid Mount failed this boot; repair its config and reboot first"
fi

ORIGINAL=/system_ext/lib64/libsurfaceflinger.so
STOCK_SHA=407be876ceadc0ac5254abcc357ed2c196fbbf6179c940bc75d1ddf05f63ae32
PREVIOUS_SHA=ef523f9d57ebe5c3af2ec39747b07ab6d70bd69165461e09ddc0fc8f13f5d563
PATCHED_SHA=7cb2123d0b5d5cfd9ae63699c9624ebe306392b88f642ec609fdb5fc2247e30f

[ -f "$ORIGINAL" ] || abort "SurfaceFlinger library is missing"
ACTUAL=$(sha256sum "$ORIGINAL" | awk '{print $1}')

PAYLOAD="$MODPATH/system/system_ext/lib64/libsurfaceflinger.so"
PATCHDIR="$MODPATH/patches"
mkdir -p "$(dirname "$PAYLOAD")"

apply_blob() {
  OFFSET="$1"
  BLOB="$2"
  [ -f "$BLOB" ] || abort "Missing patch blob: $BLOB"
  if command -v busybox >/dev/null 2>&1; then
    busybox dd if="$BLOB" of="$PAYLOAD" bs=1 seek="$OFFSET" conv=notrunc 2>/dev/null       || abort "Failed to patch offset $OFFSET"
  else
    dd if="$BLOB" of="$PAYLOAD" bs=1 seek="$OFFSET" conv=notrunc 2>/dev/null       || abort "Failed to patch offset $OFFSET"
  fi
}

case "$ACTUAL" in
  "$STOCK_SHA")
    ui_print "- Stock SurfaceFlinger verified"
    cp -f "$ORIGINAL" "$PAYLOAD" || abort "Failed to stage stock SurfaceFlinger"

    # Reproduce the previously validated per-PassBlur freshness/latest-only patch.
    apply_blob 208      "$PATCHDIR/000000d0.bin"
    apply_blob 328      "$PATCHDIR/00000148.bin"
    apply_blob 4331664  "$PATCHDIR/00421890.bin"
    apply_blob 5566876  "$PATCHDIR/0054f19c.bin"
    apply_blob 5608176  "$PATCHDIR/005592f0.bin"
    apply_blob 5611584  "$PATCHDIR/0055a040.bin"
    apply_blob 5611736  "$PATCHDIR/0055a0d8.bin"
    apply_blob 5612152  "$PATCHDIR/0055a278.bin"
    apply_blob 10809728 "$PATCHDIR/00a4f180.bin"

    # Additional pacing patch: PassBlur::PassBlur property read -> mov w0,#12.
    apply_blob 4331524 "$PATCHDIR/00421804.bin"
    ;;

  "$PREVIOUS_SHA")
    ui_print "- Previous LiquidDock native PassBlur payload verified"
    cp -f "$ORIGINAL" "$PAYLOAD" || abort "Failed to stage previous SurfaceFlinger"
    apply_blob 4331524 "$PATCHDIR/00421804.bin"
    ;;

  "$PATCHED_SHA")
    ui_print "- Current LiquidDock pacing12 payload already active"
    cp -f "$ORIGINAL" "$PAYLOAD" || abort "Failed to stage current SurfaceFlinger"
    ;;

  *)
    abort "SurfaceFlinger SHA256 differs from supported OS3.0.310.0.WAOCNXM binaries"
    ;;
esac

chmod 0644 "$PAYLOAD"
FINAL=$(sha256sum "$PAYLOAD" | awk '{print $1}')
[ "$FINAL" = "$PATCHED_SHA" ] || abort "Generated SurfaceFlinger SHA256 differs: $FINAL"

# Patch material is install-only. Keep installed module layout equivalent to the
# validated HybridMount template.
rm -rf "$PATCHDIR"

ui_print "- Exact binary generated and verified"
ui_print "- Per-PassBlur freshness/latest-only patch active"
ui_print "- Native PassBlur pacing hard-set to 12 ms"
ui_print "- Hybrid Mount will mount the patched library on next reboot"
