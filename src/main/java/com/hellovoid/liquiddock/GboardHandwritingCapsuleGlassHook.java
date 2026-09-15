package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Hooks the stable Gboard companion toolbar view without R8 implementation names. */
final class GboardHandwritingCapsuleGlassHook {
    private static final String TAG = "[DC][GboardToolbarGlass]";
    private static final String WIDGET_CLASS =
            "com.google.android.libraries.inputmethod.companionwidget.widget.WidgetSoftKeyboardView";
    private static final WeakHashMap<ViewGroup, Float> NATIVE_TOOLBAR_RADII = new WeakHashMap<>();
    private static boolean installed;

    private GboardHandwritingCapsuleGlassHook() {}

    static synchronized boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> widgetClass = Class.forName(WIDGET_CLASS, false, classLoader);
            Constructor<?> widgetConstructor = widgetClass.getDeclaredConstructor(
                    Context.class, AttributeSet.class);
            HookUtil.hook(widgetConstructor, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                float nativeRadiusPx = resolveNativeToolbarRadiusPx(args);
                Object result = chain.proceed(args);
                Object owner = chain.getThisObject();
                if (owner instanceof ViewGroup && nativeRadiusPx > 0f) {
                    rememberNativeToolbarRadius((ViewGroup) owner, nativeRadiusPx);
                }
                return result;
            });

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
            return true;
        } catch (Throwable error) {
            log("hook unavailable; stock Gboard toolbar retained", error);
            return false;
        }
    }

    private static float resolveNativeToolbarRadiusPx(Object[] args) {
        if (args == null || args.length < 2
                || !(args[0] instanceof Context) || !(args[1] instanceof AttributeSet)) return 0f;
        Context context = (Context) args[0];
        AttributeSet attrs = (AttributeSet) args[1];
        TypedArray typedArray = null;
        try {
            int clipRadiusAttr = context.getResources().getIdentifier(
                    "clipRadius", "attr", context.getPackageName());
            if (clipRadiusAttr == 0) return 0f;
            typedArray = context.obtainStyledAttributes(attrs, new int[]{clipRadiusAttr}, 0, 0);
            return typedArray.getDimension(0, 0f);
        } catch (Throwable ignored) {
            return 0f;
        } finally {
            if (typedArray != null) typedArray.recycle();
        }
    }

    private static synchronized void rememberNativeToolbarRadius(ViewGroup host, float radiusPx) {
        if (host == null || radiusPx <= 0f || Float.isNaN(radiusPx) || Float.isInfinite(radiusPx)) return;
        NATIVE_TOOLBAR_RADII.put(host, radiusPx);
    }

    private static synchronized float nativeToolbarRadiusPx(ViewGroup host) {
        Float radius = host == null ? null : NATIVE_TOOLBAR_RADII.get(host);
        return radius == null ? 0f : radius;
    }

    private static void handleWidgetLayout(ViewGroup host) {
        if (!isToolbarGeometry(host)) {
            GboardHandwritingCapsuleGlassCoordinator.onHidden(host);
            return;
        }

        float nativeRadiusPx = nativeToolbarRadiusPx(host);
        if (nativeRadiusPx <= 0f) {
            GboardHandwritingCapsuleGlassCoordinator.onHidden(host);
            return;
        }

        ConfigReader liveReader = ConfigReader.load();
        LiquidDockConfig liveConfig = LiquidDockConfig.from(liveReader);
        GboardGlassPreferences.Appearance liveAppearance =
                GboardGlassPreferences.resolve(liveReader, liveConfig.glass);
        if (!liveConfig.enabled || !liveConfig.glass.enabled || !liveAppearance.enabled) {
            GboardHandwritingCapsuleGlassCoordinator.onHidden(host);
            return;
        }
        GboardHandwritingCapsuleGlassCoordinator.onShown(host, liveConfig.glass, nativeRadiusPx);
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

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message, error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
