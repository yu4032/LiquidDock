package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

/** Hooks the stable Gboard companion toolbar view without R8 implementation names. */
final class GboardHandwritingCapsuleGlassHook {
    private static final String TAG = "[DC][GboardHandwritingGlass]";
    private static final String WIDGET_CLASS =
            "com.google.android.libraries.inputmethod.companionwidget.widget.WidgetSoftKeyboardView";
    private static final int DIAG_LIMIT = 80;
    private static int diagnosticEvents;
    private static boolean installed;

    private GboardHandwritingCapsuleGlassHook() {}

    static synchronized boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
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
            diag("installed toolbar=" + WIDGET_CLASS);
            return true;
        } catch (Throwable error) {
            log("hook unavailable; stock Gboard toolbar retained", error);
            return false;
        }
    }

    private static void handleWidgetLayout(ViewGroup host) {
        boolean toolbar = isToolbarGeometry(host);
        diag("widget-layout class=" + host.getClass().getName()
                + " attached=" + host.isAttachedToWindow()
                + " shown=" + host.isShown()
                + " vis=" + host.getVisibility()
                + " size=" + host.getWidth() + "x" + host.getHeight()
                + " children=" + host.getChildCount()
                + " toolbar=" + toolbar
                + " orientation=" + orientation(host)
                + " root=" + describe(host.getRootView())
                + " parent=" + describe(host.getParent()));
        if (!toolbar) {
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
        diag("widget-accepted size=" + host.getWidth() + "x" + host.getHeight()
                + " orientation=" + orientation(host));
        GboardHandwritingCapsuleGlassCoordinator.onShown(host, liveConfig.glass);
    }

    static boolean isToolbarGeometry(ViewGroup host) {
        if (host == null || !host.isAttachedToWindow() || !host.isShown()
                || host.getWidth() <= 0 || host.getHeight() <= 0) return false;
        View root = host.getRootView();
        if (root == null || !root.isAttachedToWindow()
                || root.getWidth() <= 0 || root.getHeight() <= 0) return false;
        float density = host.getResources().getDisplayMetrics().density;
        if (density <= 0f) density = 1f;
        return GboardHandwritingToolbarGeometryPolicy.isToolbar(
                host.getWidth(), host.getHeight(), density);
    }

    private static String orientation(ViewGroup host) {
        if (host == null) return "unknown";
        return host.getWidth() >= host.getHeight() ? "horizontal" : "vertical";
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
