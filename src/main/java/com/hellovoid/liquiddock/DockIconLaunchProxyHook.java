package com.hellovoid.liquiddock;

import android.graphics.RectF;
import android.view.View;

/** Routes Launcher 4.50 Dock ShortcutIcon animation ownership into the Launcher-root proxy glass. */
final class DockIconLaunchProxyHook {
    private static final String TAG = "[DC][DockIconProxy]";
    private static boolean installed;

    private DockIconLaunchProxyHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled
                || !runtimeConfig.glass.iconStyle.enabled) return false;
        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;
        boolean shortcut = installShortcutVisibilityHook(classLoader);
        boolean view2 = installFloatingProxyHook(classLoader,
                "com.miui.home.recents.views.FloatingIconView2", false, glassConfig);
        boolean layer2 = installFloatingProxyHook(classLoader,
                "com.miui.home.recents.views.FloatingIconLayer2", true, glassConfig);
        installed = shortcut && (view2 || layer2);
        if (installed) MainHook.log(TAG + " Dock icon launch-proxy hooks installed");
        return installed;
    }

    private static boolean installShortcutVisibilityHook(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.ShortcutIcon",
                    "setAnimTargetVisibility",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        Object owner = chain.getThisObject();
                        if (!(owner instanceof View) || args.length == 0
                                || !(args[0] instanceof Number)) return result;
                        View host = (View) owner;
                        int visibility = ((Number) args[0]).intValue();
                        if (visibility == View.VISIBLE
                                && LauncherGlassHierarchy.classify(host)
                                == LauncherGlassHierarchy.Domain.DOCK) {
                            DockIconLaunchProxyBridge.end(host);
                        }
                        return result;
                    }, int.class);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " ShortcutIcon visibility hook unavailable: " + error);
            return false;
        }
    }

    private static boolean installFloatingProxyHook(
            ClassLoader classLoader, String className, boolean useRotationRect,
            LiquidDockConfig.Glass glassConfig) {
        try {
            HookUtil.hookMethod(classLoader, className, "update",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length == 10 && args[0] instanceof RectF
                                && args[1] instanceof RectF && args[2] instanceof Number
                                && args[6] instanceof Boolean && !((Boolean) args[6])) {
                            Object owner = chain.getThisObject();
                            Object target = HookUtil.invoke(owner, "getAnimTarget");
                            if (target instanceof View) {
                                View targetView = (View) target;
                                if (LauncherGlassHierarchy.classify(targetView)
                                        == LauncherGlassHierarchy.Domain.DOCK) {
                                    float proxyAlpha = ((Number) args[2]).floatValue();
                                    boolean drawIcon;
                                    if (useRotationRect) {
                                        try {
                                            drawIcon = HookUtil.getBooleanField(owner, "mIsDrawIcon");
                                        } catch (Throwable ignored) {
                                            drawIcon = false;
                                        }
                                    } else {
                                        Object draw = HookUtil.invoke(owner, "isDrawIcon");
                                        drawIcon = draw instanceof Boolean && ((Boolean) draw);
                                    }
                                    boolean proxyVisible = useRotationRect
                                            ? LauncherGlassProxyVisibility.isLayer2Visible(
                                                    proxyAlpha, drawIcon)
                                            : LauncherGlassProxyVisibility.isView2Visible(
                                                    proxyAlpha, drawIcon);
                                    if (!proxyVisible) {
                                        DockIconLaunchProxyBridge.holdHidden(
                                                owner, targetView, glassConfig);
                                    } else {
                                        RectF proxyRect = (RectF) args[useRotationRect ? 1 : 0];
                                        DockIconLaunchProxyBridge.update(
                                                owner, targetView, proxyRect, glassConfig);
                                    }
                                }
                            }
                        }
                        return chain.proceed(args);
                    }, RectF.class, RectF.class,
                    float.class, float.class, float.class,
                    boolean.class, boolean.class, boolean.class,
                    float.class, boolean.class);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " floating proxy hook unavailable class=" + className + ": " + error);
            return false;
        }
    }
}
