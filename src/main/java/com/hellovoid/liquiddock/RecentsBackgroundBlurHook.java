package com.hellovoid.liquiddock;

import android.view.Window;

/** Limits only Launcher Recents window blur while preserving the vendor animation curves. */
final class RecentsBackgroundBlurHook {
    private static final String TAG = "[DC][RecentsBlur]";
    private static final String BLUR_UTILS = "com.miui.home.launcher.common.BlurUtils";
    private static final ThreadLocal<Boolean> RECENTS_TARGET_SCOPE = new ThreadLocal<>();
    private static boolean installed;

    private RecentsBackgroundBlurHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed) return true;
        if (config == null || !config.enabled) return false;
        int percent = config.recents.backgroundBlurPercent;
        try {
            installTargetInterceptor(classLoader, percent);
            installSynchronousTargetScope(classLoader, "fastBlurWhenEnterRecents",
                    "com.miui.home.launcher.Launcher",
                    "com.miui.home.launcher.LauncherState", boolean.class);
            installSynchronousTargetScope(classLoader, "fastBlurWhenGestureResetTaskView",
                    "com.miui.home.launcher.Launcher", boolean.class);
            installSynchronousTargetScope(classLoader, "fastBlurWhenEnterMultiWindowMode",
                    "com.miui.home.launcher.Launcher", boolean.class);
            installGestureRatioHook(classLoader,
                    "fastBlurWhenDontUseNoBlurTypeWhenRecents", percent);
            installGestureRatioHook(classLoader,
                    "fastBlurWhenUseCompleteRecentsBlur", percent);
            installed = true;
            MainHook.log(TAG + " hooks installed strength=" + percent + "%");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hooks unavailable: " + error);
            return false;
        }
    }

    private static void installTargetInterceptor(ClassLoader loader, int percent) {
        HookUtil.hookMethod(loader, BLUR_UTILS, "fastBlur", chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            if (Boolean.TRUE.equals(RECENTS_TARGET_SCOPE.get())
                    && args.length > 0 && args[0] instanceof Number
                    && ((Number) args[0]).floatValue() > 0f) {
                args[0] = RecentsBlurPolicy.ratioFromPercent(percent);
            }
            return chain.proceed(args);
        }, float.class, Window.class, boolean.class);
    }

    private static void installSynchronousTargetScope(
            ClassLoader loader, String methodName, Object... parameterTypes) {
        HookUtil.hookMethod(loader, BLUR_UTILS, methodName, chain -> {
            RECENTS_TARGET_SCOPE.set(Boolean.TRUE);
            try {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            } finally {
                RECENTS_TARGET_SCOPE.remove();
            }
        }, parameterTypes);
    }

    private static void installGestureRatioHook(
            ClassLoader loader, String methodName, int percent) {
        HookUtil.hookMethod(loader, BLUR_UTILS, methodName, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            if (args.length > 1 && args[1] instanceof Number) {
                args[1] = RecentsBlurPolicy.scaleGestureRatio(
                        ((Number) args[1]).floatValue(), percent);
            }
            return chain.proceed(args);
        }, "com.miui.home.launcher.Launcher", float.class, boolean.class);
    }
}
