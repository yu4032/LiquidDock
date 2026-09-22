package com.hellovoid.liquiddock;

import android.view.Window;

import java.lang.reflect.Constructor;

/**
 * Owns Launcher Recents background blur and dimming overrides while preserving vendor transition
 * timing and all non-Recents visual states.
 */
final class RecentsBackgroundBlurHook {
    private static final String TAG = "[DC][RecentsBlur]";
    private static final String BLUR_UTILS = "com.miui.home.launcher.common.BlurUtils";
    private static final String RECENT_BLUR_PARAMS =
            "com.miui.home.recents.anim.RecentBlurParams";
    private static final String RECENT_BLUR_PARAMS_COMPANION =
            "com.miui.home.recents.anim.RecentBlurParams$Companion";

    private static final ThreadLocal<Boolean> RECENTS_TARGET_SCOPE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> RECENTS_DIMMING_SCOPE = new ThreadLocal<>();
    private static boolean installed;

    private RecentsBackgroundBlurHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed) return true;
        if (config == null || !config.enabled) return false;
        int percent = config.recents.backgroundBlurPercent;
        boolean disableDimming = config.recents.disableWallpaperDimming;
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
            if (disableDimming) {
                installRecentsDimmingOverride(classLoader);
            }
            installed = true;
            MainHook.log(TAG + " hooks installed strength=" + percent + "%"
                    + " wallpaperDimming=" + (disableDimming ? "off" : "vendor"));
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

    /**
     * HyperOS 4.50 keeps Recents blur strength and wallpaper dimming in separate parameters.
     *
     * <p>RecentBlurParams is created synchronously by semantic state factories. Hook only those
     * factories that represent entering or remaining in Recents, then zero constructor arguments
     * {@code dimming} and {@code ezDimming}. Blur radius, spring damping and response are left
     * untouched, so the existing Recents blur-strength control and vendor animation curve remain
     * authoritative.</p>
     */
    private static void installRecentsDimmingOverride(ClassLoader loader) throws Exception {
        Class<?> params = Class.forName(RECENT_BLUR_PARAMS, false, loader);
        int hookedConstructors = 0;
        for (Constructor<?> constructor : params.getDeclaredConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length < 3
                    || types[0] != float.class
                    || types[1] != float.class
                    || types[2] != float.class) {
                continue;
            }
            constructor.setAccessible(true);
            HookUtil.hook(constructor, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (Boolean.TRUE.equals(RECENTS_DIMMING_SCOPE.get())
                        && args.length >= 3
                        && args[1] instanceof Number
                        && args[2] instanceof Number) {
                    args[1] = RecentsBlurPolicy.resolveDimming(
                            ((Number) args[1]).floatValue(), true);
                    args[2] = RecentsBlurPolicy.resolveDimming(
                            ((Number) args[2]).floatValue(), true);
                }
                return chain.proceed(args);
            });
            hookedConstructors++;
        }
        if (hookedConstructors == 0) {
            throw new NoSuchMethodException("RecentBlurParams float constructor");
        }

        installDimmingFactoryScope(loader, "getRecentStateParams");
        installDimmingFactoryScope(loader, "getHomeHoldParams");
        installDimmingFactoryScope(loader, "getRecentDragParams", float.class);
        installDimmingFactoryScope(
                loader, "getAppMoveStateParams", float.class, boolean.class, boolean.class);
    }

    private static void installDimmingFactoryScope(
            ClassLoader loader, String methodName, Object... parameterTypes) {
        HookUtil.hookMethod(loader, RECENT_BLUR_PARAMS_COMPANION, methodName, chain -> {
            Boolean previous = RECENTS_DIMMING_SCOPE.get();
            RECENTS_DIMMING_SCOPE.set(Boolean.TRUE);
            try {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            } finally {
                if (previous == null) RECENTS_DIMMING_SCOPE.remove();
                else RECENTS_DIMMING_SCOPE.set(previous);
            }
        }, parameterTypes);
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
