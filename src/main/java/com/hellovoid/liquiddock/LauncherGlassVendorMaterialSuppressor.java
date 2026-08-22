package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

/** Clears vendor material/blur ownership without hiding real widget/icon content. */
final class LauncherGlassVendorMaterialSuppressor {
    private LauncherGlassVendorMaterialSuppressor() {}

    static void claimWidget(View host) {
        if (host == null) return;
        MiBlurBridge.clearContentBlur(host);
        setMaMlBlurIfSupported(host, false);
        invokeBoolean(host, "setViewBlur", false);
        invokeBoolean(host, "setBlurIfNeed", false);
        invokeInt(host, "setBlurRadius", 0);
    }

    static void claimFolderMaterial(View material) {
        if (material == null) return;
        MiBlurBridge.clearContentBlur(material);
        invokeBoolean(material, "setViewBlur", false);
        invokeBoolean(material, "setBlurIfNeed", false);
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

    private static Method findMethod(Class<?> type, String name, Class<?> parameter) {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredMethod(name, parameter); }
            catch (NoSuchMethodException ignored) { current = current.getSuperclass(); }
        }
        return null;
    }
}
