package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Hooks stable Gboard handwriting and companion-widget views without R8 implementation names. */
final class GboardHandwritingCapsuleGlassHook {
    private static final String TAG = "[DC][GboardHandwritingGlass]";
    private static final String WIDGET_CLASS =
            "com.google.android.libraries.inputmethod.companionwidget.widget.WidgetSoftKeyboardView";
    private static final String HANDWRITING_OVERLAY_CLASS =
            "com.google.android.apps.inputmethod.libs.handwriting.keyboard.HandwritingOverlayView";
    private static final WeakHashMap<View, Boolean> ACTIVE_HANDWRITING_OVERLAYS =
            new WeakHashMap<>();
    private static final int DIAG_LIMIT = 80;
    private static int diagnosticEvents;
    private static boolean installed;

    private GboardHandwritingCapsuleGlassHook() {}

    static synchronized boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> handwritingClass = Class.forName(
                    HANDWRITING_OVERLAY_CLASS, false, classLoader);
            Method handwritingLayout = handwritingClass.getDeclaredMethod(
                    "onLayout",
                    Boolean.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE);
            HookUtil.hook(handwritingLayout, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object owner = chain.getThisObject();
                if (owner instanceof View) handleHandwritingLayout((View) owner);
                return result;
            });

            Method handwritingDetached = handwritingClass.getDeclaredMethod(
                    "onDetachedFromWindow");
            HookUtil.hook(handwritingDetached, chain -> {
                Object owner = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                if (owner instanceof View) handleHandwritingDetached((View) owner);
                return result;
            });

            Class<?> widgetClass = Class.forName(WIDGET_CLASS, false, classLoader);
            Method widgetLayout = widgetClass.getDeclaredMethod(
                    "onLayout",
                    Boolean.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                    Integer.TYPE);
            HookUtil.hook(widgetLayout, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object owner = chain.getThisObject();
                if (owner instanceof ViewGroup) handleWidgetLayout((ViewGroup) owner);
                return result;
            });

            installed = true;
            diag("installed handwriting=" + HANDWRITING_OVERLAY_CLASS
                    + " widget=" + WIDGET_CLASS);
            return true;
        } catch (Throwable error) {
            log("hook unavailable; stock handwriting widget retained", error);
            return false;
        }
    }

    private static synchronized void handleHandwritingLayout(View overlay) {
        if (overlay == null) return;
        diag("handwriting-layout attached=" + overlay.isAttachedToWindow()
                + " shown=" + overlay.isShown()
                + " vis=" + overlay.getVisibility()
                + " size=" + overlay.getWidth() + "x" + overlay.getHeight()
                + " root=" + describe(overlay.getRootView()));
        if (!overlay.isAttachedToWindow() || !overlay.isShown()) return;
        ACTIVE_HANDWRITING_OVERLAYS.put(overlay, Boolean.TRUE);
        diag("handwriting-active count=" + ACTIVE_HANDWRITING_OVERLAYS.size());
    }

    private static synchronized void handleHandwritingDetached(View overlay) {
        if (overlay != null) ACTIVE_HANDWRITING_OVERLAYS.remove(overlay);
        diag("handwriting-detached remaining=" + ACTIVE_HANDWRITING_OVERLAYS.size());
        if (!handwritingSceneActive()) {
            GboardHandwritingCapsuleGlassCoordinator.onHandwritingSceneEnded();
        }
    }

    private static synchronized boolean handwritingSceneActive() {
        ACTIVE_HANDWRITING_OVERLAYS.entrySet().removeIf(entry -> {
            View overlay = entry.getKey();
            return overlay == null || !overlay.isAttachedToWindow() || !overlay.isShown();
        });
        return !ACTIVE_HANDWRITING_OVERLAYS.isEmpty();
    }

    private static void handleWidgetLayout(ViewGroup host) {
        boolean handwriting = handwritingSceneActive();
        boolean geometry = isSideCapsuleGeometry(host);
        diag("widget-layout class=" + host.getClass().getName()
                + " attached=" + host.isAttachedToWindow()
                + " shown=" + host.isShown()
                + " vis=" + host.getVisibility()
                + " size=" + host.getWidth() + "x" + host.getHeight()
                + " children=" + host.getChildCount()
                + " handwriting=" + handwriting
                + " sideCapsule=" + geometry
                + " root=" + describe(host.getRootView())
                + " parent=" + describe(host.getParent()));
        if (!handwriting || !geometry) {
            GboardHandwritingCapsuleGlassCoordinator.onHidden(host);
            return;
        }

        ConfigReader liveReader = ConfigReader.load();
        LiquidDockConfig liveConfig = LiquidDockConfig.from(liveReader);
        GboardGlassPreferences.Appearance liveAppearance =
                GboardGlassPreferences.resolve(liveReader, liveConfig.glass);
        diag("widget-config master=" + liveConfig.enabled
                + " glass=" + liveConfig.glass.enabled
                + " gboard=" + liveAppearance.enabled);
        if (!liveConfig.enabled || !liveConfig.glass.enabled || !liveAppearance.enabled) {
            GboardHandwritingCapsuleGlassCoordinator.onHidden(host);
            return;
        }
        diag("widget-accepted size=" + host.getWidth() + "x" + host.getHeight());
        GboardHandwritingCapsuleGlassCoordinator.onShown(host, liveConfig.glass);
    }

    static boolean isSideCapsuleGeometry(ViewGroup host) {
        if (host == null || !host.isAttachedToWindow() || !host.isShown()
                || host.getWidth() <= 0 || host.getHeight() <= 0) return false;
        View root = host.getRootView();
        if (root == null || !root.isAttachedToWindow()
                || root.getWidth() <= 0 || root.getHeight() <= 0) return false;

        float density = host.getResources().getDisplayMetrics().density;
        float width = host.getWidth();
        float height = host.getHeight();
        if (density <= 0f) density = 1f;

        // Gboard's stylus companion uses a compact vertical WidgetSoftKeyboardView. Keep
        // horizontal companion widgets (voice/physical-keyboard variants) out of scope.
        boolean verticalCapsule = height >= width * 1.35f;
        boolean compactWidth = width <= 180f * density;
        boolean boundedHeight = height <= 560f * density;
        boolean meaningfulSize = width >= 28f * density && height >= 64f * density;
        return verticalCapsule && compactWidth && boundedHeight && meaningfulSize;
    }

    private static synchronized void diag(String message) {
        if (diagnosticEvents >= DIAG_LIMIT) return;
        diagnosticEvents++;
        log("DIAG " + diagnosticEvents + "/" + DIAG_LIMIT + " " + message, null);
    }

    private static String describe(Object object) {
        if (object == null) return "null";
        if (!(object instanceof View)) return object.getClass().getName();
        View view = (View) object;
        return view.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(view))
                + "[" + view.getWidth() + "x" + view.getHeight()
                + ",shown=" + view.isShown() + "]";
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message, error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
