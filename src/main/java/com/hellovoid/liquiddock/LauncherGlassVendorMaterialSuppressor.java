package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Clears vendor material ownership while preserving real widget/folder content. */
final class LauncherGlassVendorMaterialSuppressor {
    private static final Map<View, Drawable> ORIGINAL_WIDGET_BACKGROUNDS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LauncherGlassVendorMaterialSuppressor() {}

    static void claimWidget(View host) {
        if (host == null) return;
        MiBlurBridge.clearContentBlur(host);
        setMaMlBlurIfSupported(host, false);
        invokeBoolean(host, "setViewBlur", false);
        invokeBoolean(host, "setBlurIfNeed", false);
        invokeInt(host, "setBlurRadius", 0);

        // Launcher 4.50's LauncherAppWidgetHostView.setBlurIfNeed() owns only the RemoteViews
        // android.R.id.widget_frame fallback plate. Its native blur path removes that background
        // while leaving provider content untouched; LiquidDock mirrors the same ownership.
        View widgetFrame = host.findViewById(android.R.id.widget_frame);
        if (widgetFrame != null && widgetFrame != host) {
            Drawable current = widgetFrame.getBackground();
            if (current != null) ORIGINAL_WIDGET_BACKGROUNDS.put(widgetFrame, current);
            widgetFrame.setBackground(null);
        }

        // Launcher 4.50 tells MAML content that a background material is available with this
        // variable. Keep the content-side contract while LiquidDock replaces the vendor blur.
        if (isMaMlHost(host)) {
            putMaMlBackgroundBlurVariable(host, 1.0d);
        }
    }

    static void releaseWidget(View host) {
        if (host == null) return;
        View widgetFrame = host.findViewById(android.R.id.widget_frame);
        if (widgetFrame != null && widgetFrame != host) {
            Drawable original = ORIGINAL_WIDGET_BACKGROUNDS.remove(widgetFrame);
            if (original != null && widgetFrame.getBackground() == null) {
                widgetFrame.setBackground(original);
            }
        }
        if (isMaMlHost(host)) {
            putMaMlBackgroundBlurVariable(host, 0.0d);
        }
    }

    static void claimFolderMaterial(View material) {
        if (material == null) return;
        MiBlurBridge.clearContentBlur(material);
        invokeBoolean(material, "setViewBlur", false);
        invokeBoolean(material, "setBlurIfNeed", false);
    }

    private static boolean isMaMlHost(View host) {
        String name = host.getClass().getName();
        return name.endsWith(".MaMlHostView") || name.contains(".maml.");
    }

    private static void putMaMlBackgroundBlurVariable(View host, double value) {
        invokeStringDouble(host, "putVariableNumber", "enable_background_blur", value);
    }

    private static void setMaMlBlurIfSupported(View host, boolean enabled) {
        invokeBoolean(host, "setMaMlBlur", enabled);
        invokeBoolean(host, "setMamlBlur", enabled);
        invokeBoolean(host, "enableBlur", enabled);
    }

    private static void invokeBoolean(View host, String name, boolean value) {
        try {
            Method method = findMethod(host.getClass(), name, boolean.class);
            if (method == null) return;
            method.setAccessible(true);
            method.invoke(host, value);
        } catch (Throwable ignored) {}
    }

    private static void invokeInt(View host, String name, int value) {
        try {
            Method method = findMethod(host.getClass(), name, int.class);
            if (method == null) return;
            method.setAccessible(true);
            method.invoke(host, value);
        } catch (Throwable ignored) {}
    }

    private static void invokeStringDouble(
            View host, String name, String key, double value) {
        try {
            Method method = findMethod(host.getClass(), name, String.class, double.class);
            if (method == null) return;
            method.setAccessible(true);
            method.invoke(host, key, value);
        } catch (Throwable ignored) {}
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredMethod(name, parameters); }
            catch (NoSuchMethodException ignored) { current = current.getSuperclass(); }
        }
        return null;
    }
}
