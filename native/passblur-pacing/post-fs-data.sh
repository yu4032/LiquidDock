#!/system/bin/sh
MODDIR=${0%/*}
PROP="persist.sys.sf.draw.texture"
CONFIG="$MODDIR/interval_ms"
ORIGINAL="$MODDIR/original_value"

if [ ! -f "$ORIGINAL" ]; then
    getprop "$PROP" > "$ORIGINAL"
fi

INTERVAL="$(cat "$CONFIG" 2>/dev/null)"
case "$INTERVAL" in
    ''|*[!0-9]*) INTERVAL=12 ;;
esac

# Keep the experiment within sane bounds. HyperOS interprets this property in milliseconds;
# for sfScale=1.0 the decompiled PassBlur gate uses interval/2.
if [ "$INTERVAL" -lt 4 ] || [ "$INTERVAL" -gt 50 ]; then
    INTERVAL=12
fi

resetprop -n "$PROP" "$INTERVAL"
