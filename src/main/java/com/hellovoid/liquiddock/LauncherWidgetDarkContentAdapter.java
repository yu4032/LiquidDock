package com.hellovoid.liquiddock;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Opt-in content adaptation for widgets rendered over LiquidDock glass. */
final class LauncherWidgetDarkContentAdapter {
    private static final Map<TextView, ColorStateList> ORIGINAL_TEXT_COLORS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Double> ORIGINAL_MAML_DARK_MODE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LauncherWidgetDarkContentAdapter() {}

    static void apply(View host) {
        if (host == null) return;
        if (!LauncherWidgetGlassSelection.isEnabled(host)) {
            release(host);
            return;
        }
        if (isMaMlHost(host)) {
            applyMaml(host);
            return;
        }

        // Standard RemoteViews stay in the provider/launcher configuration that actually exists.
        // Only adapt dark-neutral foreground text; do not force provider night resources or reinflate.
        applyTextTree(host);
    }

    static void release(View host) {
        if (host == null) return;
        if (isMaMlHost(host)) {
            Double original = ORIGINAL_MAML_DARK_MODE.remove(host);
            if (original != null) {
                HookUtil.invoke(host, "putVariableNumber", "__darkmode", original);
                HookUtil.invoke(host, "requestUpdate");
            }
            return;
        }

        releaseTextTree(host);
    }

    private static void applyMaml(View host) {
        if (!ORIGINAL_MAML_DARK_MODE.containsKey(host)) {
            Object value = HookUtil.invoke(host, "getVariableNumber", "__darkmode");
            ORIGINAL_MAML_DARK_MODE.put(host, value instanceof Number
                    ? ((Number) value).doubleValue() : 0.0d);
        }
        HookUtil.invoke(host, "putVariableNumber", "__darkmode", 1.0d);
        HookUtil.invoke(host, "requestUpdate");
    }

    private static void applyTextTree(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            ColorStateList current = text.getTextColors();
            if (current != null && isDarkNeutral(current.getDefaultColor())) {
                ORIGINAL_TEXT_COLORS.put(text, current);
                text.setTextColor(Color.WHITE);
            } else if (current != null && current.getDefaultColor() != Color.WHITE) {
                ORIGINAL_TEXT_COLORS.remove(text);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTextTree(group.getChildAt(i));
        }
    }

    private static void releaseTextTree(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            ColorStateList original = ORIGINAL_TEXT_COLORS.remove(text);
            if (original != null) text.setTextColor(original);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) releaseTextTree(group.getChildAt(i));
        }
    }

    static boolean isDarkNeutral(int color) {
        if (Color.alpha(color) == 0) return false;
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max - min > 36) return false;
        double luminance = (0.2126d * r + 0.7152d * g + 0.0722d * b) / 255.0d;
        return luminance <= 0.42d;
    }

    private static boolean isMaMlHost(View host) {
        String name = host.getClass().getName();
        return name.endsWith(".MaMlHostView") || name.contains(".maml.");
    }
}
