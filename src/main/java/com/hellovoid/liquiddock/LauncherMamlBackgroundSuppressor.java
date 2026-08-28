package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Exact MAML background ownership rules backed by inspected widget payloads and live roots. */
final class LauncherMamlBackgroundSuppressor {
    // HyperOS 3 Weather "Today's weather" MAML on Launcher 4.50.
    // Live ScreenElementRoot.mElements proves this one semantic group owns the old/new cross-fade
    // gradient rectangles. Hide only the group; leave clouds, effects, text and weather icon intact.
    private static final String WEATHER_PRODUCT_ID =
            "b8006e83-c497-4642-9815-f674b82842b0";
    private static final String WEATHER_SKY_COLOR_ELEMENT = "sky_color_ou1b4i";
    private static final String LOG_TAG = "[MamlWidgetBg]";
    private static final String DUMP_LOG_TAG = "[MamlWidgetBgDump]";
    private static final int DUMP_CHUNK_SIZE = 16;

    private static final Map<View, Claim> CLAIMS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> DUMPED_ROOTS =
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
            dumpNamedElementsOnce(productId, root);
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

    /**
     * Launcher 4.50 ScreenElementRoot.findElement() reads its private mElements registry directly.
     * If the expected live owner is absent on a future payload, dump that same registry exactly once
     * per root. This is diagnostic-only: no visitor, show(), removeElement(), or tree mutation.
     */
    private static void dumpNamedElementsOnce(String productId, Object root) {
        if (root == null) return;
        synchronized (DUMPED_ROOTS) {
            if (DUMPED_ROOTS.containsKey(root)) return;
            DUMPED_ROOTS.put(root, Boolean.TRUE);
        }

        Object value = readField(root, "mElements");
        if (!(value instanceof Map)) {
            MainHook.log(DUMP_LOG_TAG + " productId=" + productId
                    + " registry=mElements unavailable");
            return;
        }

        Map<?, ?> elements = (Map<?, ?>) value;
        List<String> names = new ArrayList<>(elements.size());
        for (Map.Entry<?, ?> entry : elements.entrySet()) {
            String name = String.valueOf(entry.getKey());
            Object stored = entry.getValue();
            Object element = stored instanceof WeakReference
                    ? ((WeakReference<?>) stored).get() : stored;
            String type = element != null ? element.getClass().getSimpleName() : "collected";
            names.add(name + ":" + type);
        }
        Collections.sort(names);

        int chunks = Math.max(1, (names.size() + DUMP_CHUNK_SIZE - 1) / DUMP_CHUNK_SIZE);
        if (names.isEmpty()) {
            MainHook.log(DUMP_LOG_TAG + " productId=" + productId + " count=0 chunk=1/1 names=[]");
            return;
        }
        for (int chunk = 0; chunk < chunks; chunk++) {
            int from = chunk * DUMP_CHUNK_SIZE;
            int to = Math.min(names.size(), from + DUMP_CHUNK_SIZE);
            MainHook.log(DUMP_LOG_TAG + " productId=" + productId
                    + " count=" + names.size()
                    + " chunk=" + (chunk + 1) + "/" + chunks
                    + " names=" + names.subList(from, to));
        }
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
