package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Hooks the stable Gboard companion toolbar view without R8 implementation names. */
final class GboardHandwritingCapsuleGlassHook {
    private static final String TAG = "[DC][GboardToolbarGlass]";
    private static final String WIDGET_CLASS =
            "com.google.android.libraries.inputmethod.companionwidget.widget.WidgetSoftKeyboardView";
    private static final String SHADOWED_WIDGET_CLASS =
            "com.google.android.libraries.inputmethod.widgets.ShadowedSoftKeyboardView";
    private static boolean installed;

    private GboardHandwritingCapsuleGlassHook() {}

    static synchronized boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> widgetClass = Class.forName(WIDGET_CLASS, false, classLoader);
            Class<?> shadowedWidgetClass = Class.forName(SHADOWED_WIDGET_CLASS, false, classLoader);
            if (!shadowedWidgetClass.isAssignableFrom(widgetClass)) {
                throw new IllegalStateException("Gboard toolbar shadow hierarchy changed");
            }

            Method dispatchDraw = resolveDispatchDraw();
            Method shadowedDraw = shadowedWidgetClass.getDeclaredMethod("draw", Canvas.class);
            HookUtil.hook(shadowedDraw, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object owner = chain.getThisObject();
                if (!(owner instanceof ViewGroup)
                        || !widgetClass.isInstance(owner)
                        || args.length != 1
                        || !(args[0] instanceof Canvas)
                        || !GboardHandwritingCapsuleGlassCoordinator.isActive((ViewGroup) owner)) {
                    return chain.proceed(args);
                }

                Canvas canvas = (Canvas) args[0];
                try {
                    // Draw only toolbar children. This deliberately skips both the ordinary View
                    // background and ShadowedSoftKeyboardView's vendor material/clipPath so the
                    // Prismal sibling is the sole owner of the toolbar silhouette.
                    return dispatchDraw.invoke(owner, canvas);
                } catch (Throwable error) {
                    log("unable to bypass toolbar background; stock draw restored", unwrap(error));
                    return chain.proceed(args);
                }
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

    private static Method resolveDispatchDraw() throws NoSuchMethodException {
        Method method = ViewGroup.class.getDeclaredMethod("dispatchDraw", Canvas.class);
        method.setAccessible(true);
        return method;
    }

    private static void handleWidgetLayout(ViewGroup host) {
        if (!isToolbarGeometry(host)) {
            GboardHandwritingCapsuleGlassCoordinator.onHidden(host);
            return;
        }

        float cornerRadiusPx = toolbarCornerRadiusPx(host);
        if (cornerRadiusPx <= 0f) {
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
        GboardHandwritingCapsuleGlassCoordinator.onShown(host, liveConfig.glass, cornerRadiusPx);
    }

    private static float toolbarCornerRadiusPx(ViewGroup host) {
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return 0f;
        return Math.min(host.getWidth(), host.getHeight()) * 0.5f;
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

    private static Throwable unwrap(Throwable error) {
        if (error instanceof InvocationTargetException) {
            Throwable cause = ((InvocationTargetException) error).getCause();
            if (cause != null) return cause;
        }
        return error;
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message, error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
