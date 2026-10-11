package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Low-level Launcher material ownership; contains no widget-specific MAML rules. */
final class LauncherGlassVendorMaterialSuppressor {
    private static final Map<View, OwnedValueState<Drawable>> WIDGET_BACKGROUND_OWNERSHIP =
            Collections.synchronizedMap(new WeakHashMap<>());
    // MAML variables belong to a particular ScreenElementRoot generation. The root reference
    // must be weak because ScreenElementRoot can retain its View and this map has weak host keys.
    private static final Map<View, MamlBackgroundClaim> MAML_BACKGROUND_OWNERSHIP =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class MamlBackgroundClaim {
        final WeakReference<Object> rootRef;
        final OwnedValueState<Double> value = new OwnedValueState<>();

        MamlBackgroundClaim(Object root, double original) {
            rootRef = new WeakReference<>(root);
            value.claim(original);
        }
    }

    private LauncherGlassVendorMaterialSuppressor() {}

    static void claimWidgetMaterial(View host) {
        if (host == null) return;
        // Capture the provider's original MAML variable before invoking any vendor blur
        // setters. Some setters themselves change enable_background_blur.
        if (isMaMlHost(host)) rememberMamlBackground(host, readMamlRoot(host));
        MiBlurBridge.clearContentBlur(host);
        setMaMlBlurIfSupported(host, false);
        invokeBoolean(host, "setViewBlur", false);
        invokeBoolean(host, "setBlurIfNeed", false);
        invokeInt(host, "setBlurRadius", 0);

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

        // This variable is Launcher/MAML vendor material state, not a product-specific hide rule.
        if (isMaMlHost(host) && MAML_BACKGROUND_OWNERSHIP.containsKey(host)) {
            putMaMlBackgroundBlurVariable(host, 1.0d);
        }
    }

    /**
     * A MAML root may be initialized after the host was first claimed. In that case the original
     * variable must be sampled from the newly loaded root, not guessed from a null mRoot.
     */
    static void claimLoadedMamlRoot(View host, Object root) {
        if (host == null || root == null || !isMaMlHost(host)) return;
        rememberMamlBackground(host, root);
        if (MAML_BACKGROUND_OWNERSHIP.containsKey(host)) {
            putMaMlBackgroundBlurVariable(host, 1.0d);
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
        if (isMaMlHost(host)) restoreMamlBackground(host);
    }

    private static void rememberMamlBackground(View host, Object root) {
        if (root == null) return;
        MamlBackgroundClaim previous = MAML_BACKGROUND_OWNERSHIP.get(host);
        if (previous != null && previous.rootRef.get() == root) return;
        // A new ScreenElementRoot is a new provider-owned variable namespace. Never copy
        // the old root's saved value into it.
        MAML_BACKGROUND_OWNERSHIP.remove(host);
        Double original = getMaMlBackgroundBlurVariable(host);
        if (original != null && Double.isFinite(original)) {
            MAML_BACKGROUND_OWNERSHIP.put(host, new MamlBackgroundClaim(root, original));
        }
    }

    private static void restoreMamlBackground(View host) {
        MamlBackgroundClaim claim = MAML_BACKGROUND_OWNERSHIP.remove(host);
        if (claim == null) return;
        Object currentRoot = readMamlRoot(host);
        Double current = getMaMlBackgroundBlurVariable(host);
        boolean owned = currentRoot != null && currentRoot == claim.rootRef.get()
                && current != null && Double.compare(current, 1.0d) == 0;
        OwnedValueState.ReleaseDecision<Double> decision = claim.value.release(owned);
        if (decision.restoreOriginal && decision.originalValue != null) {
            putMaMlBackgroundBlurVariable(host, decision.originalValue);
        }
    }

    private static Object readMamlRoot(View host) {
        try { return HookUtil.getField(host, "mRoot"); }
        catch (Throwable ignored) { return null; }
    }

    private static Double getMaMlBackgroundBlurVariable(View host) {
        try {
            Method method = findMethod(host.getClass(), "getVariableNumber", String.class);
            if (method == null) return null;
            method.setAccessible(true);
            Object value = method.invoke(host, "enable_background_blur");
            return value instanceof Number ? ((Number) value).doubleValue() : null;
        } catch (Throwable ignored) {
            return null;
        }
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
