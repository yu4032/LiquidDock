package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Low-level Launcher material ownership; contains no widget-specific MAML rules. */
final class LauncherGlassVendorMaterialSuppressor {
    private static final Map<View, Drawable> ORIGINAL_WIDGET_BACKGROUNDS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LauncherGlassVendorMaterialSuppressor() {}

    static void claimWidgetMaterial(View host) {
        if (host == null) return;
        MiBlurBridge.clearContentBlur(host);
        setMaMlBlurIfSupported(host, false);
        invokeBoolean(host, "setViewBlur", false);
        invokeBoolean(host, "setBlurIfNeed", false);
        invokeInt(host, "setBlurRadius", 0);

        // Launcher 4.50 LauncherAppWidgetHostView.updateAppWidget() only calls setBlurIfNeed()
        // when the AppWidgetHostView has exactly one RemoteViews content child. setBlurIfNeed()
        // then resolves android.R.id.widget_frame recursively from that child. Mirror that exact
        // owner path so nested widget-frame backgrounds are covered without walking arbitrary
        // provider descendants or guessing by class/resource names.
        View remoteViewsContent = resolveRemoteViewsContent(host);
        if (remoteViewsContent != null) {
            Drawable current = remoteViewsContent.getBackground();
            if (current != null) ORIGINAL_WIDGET_BACKGROUNDS.put(remoteViewsContent, current);
            remoteViewsContent.setBackground(null);
        }

        // This variable is Launcher/MAML vendor material state, not a product-specific hide rule.
        if (isMaMlHost(host)) putMaMlBackgroundBlurVariable(host, 1.0d);
    }

    static void releaseWidgetMaterial(View host) {
        if (host == null) return;
        View remoteViewsContent = resolveRemoteViewsContent(host);
        if (remoteViewsContent != null) {
            Drawable original = ORIGINAL_WIDGET_BACKGROUNDS.remove(remoteViewsContent);
            if (original != null && remoteViewsContent.getBackground() == null) {
                remoteViewsContent.setBackground(original);
            }
        }
        if (isMaMlHost(host)) putMaMlBackgroundBlurVariable(host, 0.0d);
    }

    static void claimFolderMaterial(View material) {
        if (material == null) return;
        MiBlurBridge.clearContentBlur(material);
        invokeBoolean(material, "setViewBlur", false);
        invokeBoolean(material, "setBlurIfNeed", false);
    }

    private static View resolveRemoteViewsContent(View host) {
        if (!(host instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) host;
        if (group.getChildCount() != 1) return null;
        View content = group.getChildAt(0);
        if (content == null) return null;
        return content.findViewById(android.R.id.widget_frame);
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
