package com.hellovoid.liquiddock;

import android.view.View;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Exact MAML background ownership rules backed by inspected widget payloads. */
final class LauncherMamlBackgroundSuppressor {
    // HyperOS 3 Personal Assistant 15.30.35 / Weather "Today's weather" 2x2 payload.
    // The sky background is one semantic owner group containing the two cross-fade gradients.
    private static final String WEATHER_PRODUCT_ID =
            "b8006e83-c497-4642-9815-f674b82842b0";
    private static final String WEATHER_SKY_COLOR_ELEMENT = "sky_color_7x3ebn";
    private static final String LOG_TAG = "[MamlWidgetBg]";

    private static final Map<View, Claim> CLAIMS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LauncherMamlBackgroundSuppressor() {}

    static void claim(View host) {
        if (host == null) return;
        claimLoadedRoot(host, readField(host, "mRoot"));
    }

    static void claimLoadedRoot(View host, Object root) {
        if (host == null) return;
        Object itemInfo = HookUtil.invoke(host, "getItemInfo");
        String productId = readStringField(itemInfo, "productId");
        if (!WEATHER_PRODUCT_ID.equals(productId)) {
            release(host);
            MainHook.log(LOG_TAG + " productId=" + productId
                    + " weather=false rootLoaded=" + (root != null));
            return;
        }

        if (root == null) {
            MainHook.log(LOG_TAG + " productId=" + productId
                    + " root=null targetFound=false suppressed=false");
            return;
        }
        Object target = HookUtil.invoke(root, "findElement", WEATHER_SKY_COLOR_ELEMENT);
        if (target == null) {
            MainHook.log(LOG_TAG + " productId=" + productId
                    + " root=" + root.getClass().getSimpleName()
                    + " target=" + WEATHER_SKY_COLOR_ELEMENT
                    + " targetFound=false suppressed=false");
            return;
        }

        Claim previous = CLAIMS.get(host);
        if (previous != null && previous.element != target) {
            restore(previous);
            CLAIMS.remove(host);
            previous = null;
        }
        if (previous == null) {
            boolean originalShow = readBooleanField(target, "mShow", true);
            CLAIMS.put(host, new Claim(target, originalShow));
        }

        // Hide the one semantic sky-background owner. Do not traverse or mutate provider content.
        HookUtil.invoke(target, "show", false);
        boolean suppressed = !readBooleanField(target, "mShow", true);
        MainHook.log(LOG_TAG + " productId=" + productId
                + " root=" + root.getClass().getSimpleName()
                + " target=" + WEATHER_SKY_COLOR_ELEMENT
                + " targetFound=true suppressed=" + suppressed);
    }

    static void release(View host) {
        if (host == null) return;
        Claim claim = CLAIMS.remove(host);
        if (claim != null) restore(claim);
    }

    private static void restore(Claim claim) {
        HookUtil.invoke(claim.element, "show", claim.originalShow);
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        try { return HookUtil.getField(target, name); }
        catch (Throwable ignored) { return null; }
    }

    private static String readStringField(Object target, String name) {
        Object value = readField(target, name);
        return value instanceof String ? (String) value : null;
    }

    private static boolean readBooleanField(Object target, String name, boolean fallback) {
        if (target == null) return fallback;
        try { return HookUtil.getBooleanField(target, name); }
        catch (Throwable ignored) { return fallback; }
    }

    private static final class Claim {
        final Object element;
        final boolean originalShow;

        Claim(Object element, boolean originalShow) {
            this.element = element;
            this.originalShow = originalShow;
        }
    }
}
