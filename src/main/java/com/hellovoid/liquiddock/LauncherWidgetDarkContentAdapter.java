package com.hellovoid.liquiddock;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Opt-in content adaptation for widgets rendered over LiquidDock glass. */
final class LauncherWidgetDarkContentAdapter {
    private static final Map<TextView, ColorStateList> ORIGINAL_TEXT_COLORS =
            Collections.synchronizedMap(new WeakHashMap<>());
    // MAML root replacement is asynchronous. Never replay a dark-mode snapshot
    // taken from a previous ScreenElementRoot generation onto a new provider root.
    private static final Map<View, MamlDarkClaim> ORIGINAL_MAML_DARK_MODE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class MamlDarkClaim {
        final WeakReference<Object> root;
        final double originalValue;

        MamlDarkClaim(Object root, double originalValue) {
            this.root = new WeakReference<>(root);
            this.originalValue = originalValue;
        }
    }

    private LauncherWidgetDarkContentAdapter() {}

    static void apply(View host) {
        if (host == null) return;
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
            MamlDarkClaim claim = ORIGINAL_MAML_DARK_MODE.remove(host);
            if (claim != null) {
                Object root = readMamlRoot(host);
                Object current = invokeOptional(host, "getVariableNumber", "__darkmode");
                if (root != null && root == claim.root.get()
                        && current instanceof Number
                        && Double.compare(((Number) current).doubleValue(), 1.0d) == 0) {
                    invokeOptional(host, "putVariableNumber", "__darkmode", claim.originalValue);
                    invokeOptional(host, "requestUpdate");
                }
            }
            return;
        }

        releaseTextTree(host);
    }

    private static void applyMaml(View host) {
        Object root = readMamlRoot(host);
        if (root == null) return;
        MamlDarkClaim claim = ORIGINAL_MAML_DARK_MODE.get(host);
        if (claim == null || claim.root.get() != root) {
            Object value = invokeOptional(host, "getVariableNumber", "__darkmode");
            if (!(value instanceof Number)) return;
            double original = ((Number) value).doubleValue();
            if (!Double.isFinite(original)) return;
            ORIGINAL_MAML_DARK_MODE.put(host, new MamlDarkClaim(root, original));
        }
        invokeOptional(host, "putVariableNumber", "__darkmode", 1.0d);
        invokeOptional(host, "requestUpdate");
    }

    private static Object readMamlRoot(View host) {
        try { return HookUtil.getField(host, "mRoot"); }
        catch (Throwable ignored) { return null; }
    }

    private static Object invokeOptional(Object target, String methodName, Object... args) {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(target, methodName, args);
        if (!result.succeeded()) {
            MainHook.log("[DC][WidgetDarkContent] " + methodName + " unavailable: " + result.failure());
            return null;
        }
        return result.value();
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
            if (original != null
                    && text.getCurrentTextColor() == Color.WHITE) text.setTextColor(original);
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
