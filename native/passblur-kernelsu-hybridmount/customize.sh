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
[ -f "$ORIGINAL" ] || abort "SurfaceFlinger library is missing"

ACTUAL=$(sha256sum "$ORIGINAL" | awk '{print $1}')
case "$ACTUAL" in
  407be876ceadc0ac5254abcc357ed2c196fbbf6179c940bc75d1ddf05f63ae32)
    ui_print "- Stock SurfaceFlinger verified"
    ;;
  ef523f9d57ebe5c3af2ec39747b07ab6d70bd69165461e09ddc0fc8f13f5d563)
    ui_print "- Previous LiquidDock native PassBlur payload detected"
    ;;
  7cb2123d0b5d5cfd9ae63699c9624ebe306392b88f642ec609fdb5fc2247e30f)
    ui_print "- Current LiquidDock native PassBlur payload already active"
    ;;
  *)
    abort "SurfaceFlinger SHA256 differs from supported OS3.0.310.0.WAOCNXM build"
    ;;
esac

PAYLOAD="$MODPATH/system/system_ext/lib64/libsurfaceflinger.so"
[ -f "$PAYLOAD" ] || abort "Patched library missing from ZIP"
ACTUAL=$(sha256sum "$PAYLOAD" | awk '{print $1}')
[ "$ACTUAL" = '7cb2123d0b5d5cfd9ae63699c9624ebe306392b88f642ec609fdb5fc2247e30f' ]   || abort "Patched library SHA256 differs"

ui_print "- Exact binary verified"
ui_print "- Keeps existing per-PassBlur freshness/latest-only patch"
ui_print "- Native PassBlur pacing hard-set to 12 ms before x1,000,000 conversion"
ui_print "- Hybrid Mount will mount the patched library on next reboot"
