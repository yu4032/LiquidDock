#!/system/bin/sh
MODDIR=${0%/*}
PROP="persist.sys.sf.draw.texture"
ORIGINAL="$MODDIR/original_value"

if [ -f "$ORIGINAL" ]; then
    VALUE="$(cat "$ORIGINAL" 2>/dev/null)"
    if [ -n "$VALUE" ]; then
        resetprop -n "$PROP" "$VALUE"
    else
        # The decompiled PassBlur constructor uses 30 ms when the property is absent.
        resetprop -n "$PROP" "30"
    fi
fi
