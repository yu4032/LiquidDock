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
    private static final Map<View, OwnedValueState<Drawable>> WIDGET_BACKGROUND_OWNERSHIP =
            Collections.synchronizedMap(new WeakHashMap<>());
    // RemoteViews fallback drawables are separate from the native compositor material.
    // The actual View blur mode, radius, blend colors, PassWindow gate, and MAML
    // enable_background_blur variable remain owned by Launcher throughout App/HOME.
    
    private LauncherGlassVendorMaterialSuppressor() {}

    static void claimWidgetMaterial(View host) {
        if (host == null) return;
        // Suppress only the native blur output. Changing provider blur variables or
        // PassWindow state here would make Launcher lifecycle snapshots stale.
        LauncherWidgetNativeViewBlurGate.claim(host);

        // RemoteViews stores its layout id under android.R.id.widget_frame as a keyed tag on the
        // direct content root. Launcher 4.50 clears exactly that root background when native widget
        // blur owns the fallback plate; mirror that ownership without touching provider children.
        View remoteViewsContent = resolveRemoteViewsContent(host);
        if (remoteViewsContent != null) {
            Drawable current = remoteViewsContent.getBackground();
            if (current != null) {
                OwnedValueState<Drawable> ownership = WIDGET_BACKGROUND_OWNERSHIP
                        .computeIfAbsent(remoteViewsContent, ignored -> new OwnedValueState<>());
                ownership.claim(current);
            }
            remoteViewsContent.setBackground(null);
        }

    }

    static void releaseWidgetMaterial(View host) {
        if (host == null) return;
        View remoteViewsContent = resolveRemoteViewsContent(host);
        if (remoteViewsContent != null) {
            OwnedValueState<Drawable> ownership =
                    WIDGET_BACKGROUND_OWNERSHIP.remove(remoteViewsContent);
            if (ownership != null) {
                OwnedValueState.ReleaseDecision<Drawable> release =
                        ownership.release(remoteViewsContent.getBackground() == null);
                if (release.restoreOriginal && release.originalValue != null) {
                    remoteViewsContent.setBackground(release.originalValue);
                }
            }
        }
        // Launcher is the only authority for native material. On release, its own
        // initializer recreates the current blur mode, radius, blend list and MAML state.
        // There is no saved PassWindow/MAML value to overwrite a later App -> HOME update.
        LauncherWidgetNativeViewBlurGate.release(host);
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
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child != null && child.getTag(android.R.id.widget_frame) instanceof Integer) {
                return child;
            }
        }
        return null;
    }

    private static void invokeBoolean(View host, String name, boolean value) {
        try {
            Method method = findMethod(host.getClass(), name, boolean.class);
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
