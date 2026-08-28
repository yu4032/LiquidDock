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
    // HyperOS 3 Weather MAML on Launcher 4.50. The bundled default product is known, while
    // downloaded catalogs may expose other size/product variants. MaMlWidgetInfo persists the
    // exact bound app package, and Weather description.xml binds com.miui.weather2.
    private static final String WEATHER_PRODUCT_ID =
            "b8006e83-c497-4642-9815-f674b82842b0";
    private static final String WEATHER_LARGE_PRODUCT_ID =
            "c989887f-fa0d-4963-8c57-896c03e37efc";
    private static final String WEATHER_WIDE_PRODUCT_ID =
            "bc0f0cd2-43fd-4323-8061-55a8bc997e1f";
    private static final String WEATHER_APP_PACKAGE = "com.miui.weather2";
    // Decompiled payload + live ScreenElementRoot.mElements show stable parent "skyColor" owns
    // both the full-size old/new gradient subgroup and the following full-size glow image. Hide
    // only that parent; higher/lower weather effects, information and weather icon stay as siblings.
    private static final String WEATHER_SKY_OWNER_ELEMENT = "skyColor";
    private static final String WEATHER_BACKGROUND_OWNER_ELEMENT = "background";
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
        String appPackage = readStringField(itemInfo, "appPackage");
        int spanX = readIntField(itemInfo, "spanX", -1);
        int spanY = readIntField(itemInfo, "spanY", -1);
        int configSpanX = readIntField(itemInfo, "configSpanX", -1);
        int configSpanY = readIntField(itemInfo, "configSpanY", -1);
        boolean weather = isWeatherIdentity(productId, appPackage);

        if (!weather) {
            release(host);
            MainHook.log(LOG_TAG + " productId=" + productId
                    + " appPackage=" + appPackage
                    + " span=" + spanX + "x" + spanY
                    + " configSpan=" + configSpanX + "x" + configSpanY
                    + " weather=false rootLoaded=" + (root != null));
            return;
        }

        String identity = " productId=" + productId
                + " appPackage=" + appPackage
                + " span=" + spanX + "x" + spanY
                + " configSpan=" + configSpanX + "x" + configSpanY;
        if (root == null) {
            MainHook.log(LOG_TAG + identity
                    + " root=null targetFound=false suppressed=false");
            return;
        }
        String ownerElement = resolveWeatherBackgroundOwner(productId);
        if (ownerElement == null) {
            release(host);
            MainHook.log(LOG_TAG + identity
                    + " root=" + root.getClass().getSimpleName()
                    + " target=unresolved targetFound=false suppressed=false");
            dumpNamedElementsOnce(productId, root);
            return;
        }
        Object target = HookUtil.invoke(root, "findElement", ownerElement);
        if (target == null) {
            MainHook.log(LOG_TAG + identity
                    + " root=" + root.getClass().getSimpleName()
                    + " target=" + ownerElement
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
        MainHook.log(LOG_TAG + identity
                + " root=" + root.getClass().getSimpleName()
                + " target=" + ownerElement
                + " targetFound=true suppressed=" + suppressed);
    }

    private static boolean isWeatherIdentity(String productId, String appPackage) {
        return WEATHER_PRODUCT_ID.equals(productId)
                || WEATHER_LARGE_PRODUCT_ID.equals(productId)
                || WEATHER_WIDE_PRODUCT_ID.equals(productId)
                || WEATHER_APP_PACKAGE.equals(appPackage);
    }

    private static String resolveWeatherBackgroundOwner(String productId) {
        if (WEATHER_PRODUCT_ID.equals(productId)) return WEATHER_SKY_OWNER_ELEMENT;
        if (WEATHER_LARGE_PRODUCT_ID.equals(productId)
                || WEATHER_WIDE_PRODUCT_ID.equals(productId)) {
            return WEATHER_BACKGROUND_OWNER_ELEMENT;
        }
        return null;
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

    private static int readIntField(Object target, String name, int fallback) {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
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
