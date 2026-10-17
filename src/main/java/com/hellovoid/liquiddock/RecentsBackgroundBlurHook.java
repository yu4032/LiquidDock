package com.hellovoid.liquiddock;

import android.view.Window;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final String RECENTS_DIM_LAYER =
            "com.miui.home.recents.DimLayer";
    private static final AtomicInteger dimLayerSuppressLogCount = new AtomicInteger();

    private static final ThreadLocal<Boolean> RECENTS_TARGET_SCOPE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> RECENTS_DIMMING_SCOPE = new ThreadLocal<>();
    private static boolean installed;
    private static boolean recentParamsInstalled;
    private static boolean directDimInstalled;
    private static volatile int liveBlurPercent;
    private static volatile boolean liveDisableDimming;

    static void onLiveConfigChanged(LiquidDockConfig.Recents config) {
        if (config == null) return;
        liveBlurPercent = config.backgroundBlurPercent;
        boolean previous = liveDisableDimming;
        liveDisableDimming = config.disableWallpaperDimming;
        if (installed && previous != liveDisableDimming) {
            logDimmingCoverage();
        }
    }

    private RecentsBackgroundBlurHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed) return true;
        if (config == null || !config.enabled) return false;
        onLiveConfigChanged(config.recents);
        // OEM 4.50 variants may inline/rename any ONE blur or gesture
        // function. Never let an unrelated optional method failure prevent
        // the independent wallpaper-dimming Hooks from installing.
        boolean anyInstalled = false;
        try {
            installTargetInterceptor(classLoader);
            anyInstalled = true;
        } catch (Throwable error) {
            MainHook.log(TAG + " fastBlur strength interceptor unavailable: " + error);
        }
        anyInstalled |= tryInstallTargetScope(classLoader, "fastBlurWhenEnterRecents",
                "com.miui.home.launcher.Launcher",
                "com.miui.home.launcher.LauncherState", boolean.class);
        anyInstalled |= tryInstallTargetScope(classLoader, "fastBlurWhenGestureResetTaskView",
                "com.miui.home.launcher.Launcher", boolean.class);
        anyInstalled |= tryInstallTargetScope(classLoader, "fastBlurWhenEnterMultiWindowMode",
                "com.miui.home.launcher.Launcher", boolean.class);
        anyInstalled |= tryInstallGestureRatioHook(
                classLoader, "fastBlurWhenDontUseNoBlurTypeWhenRecents");
        anyInstalled |= tryInstallGestureRatioHook(
                classLoader, "fastBlurWhenUseCompleteRecentsBlur");
        try {
            installRecentsDimmingOverride(classLoader);
            recentParamsInstalled = true;
            anyInstalled = true;
        } catch (Throwable error) {
            MainHook.log(TAG + " RecentBlurParams dimming hooks unavailable: " + error);
        }
        try {
            installRecentsDimLayerOverride(classLoader);
            directDimInstalled = true;
            anyInstalled = true;
        } catch (Throwable error) {
            MainHook.log(TAG + " Recents DimLayer hook unavailable: " + error);
        }
        installed = anyInstalled;
        MainHook.log(TAG + " installed=" + installed
                + " strength=" + liveBlurPercent + "%");
        logDimmingCoverage();
        return installed;
    }

    private static boolean tryInstallTargetScope(
            ClassLoader loader, String name, Object... parameterTypes) {
        try {
            installSynchronousTargetScope(loader, name, parameterTypes);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " blur target scope unavailable " + name + ": " + error);
            return false;
        }
    }

    private static boolean tryInstallGestureRatioHook(ClassLoader loader, String name) {
        try {
            installGestureRatioHook(loader, name);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " blur gesture hook unavailable " + name + ": " + error);
            return false;
        }
    }

    private static void installTargetInterceptor(ClassLoader loader) {
        HookUtil.hookMethod(loader, BLUR_UTILS, "fastBlur", chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            if (Boolean.TRUE.equals(RECENTS_TARGET_SCOPE.get())
                    && args.length > 0 && args[0] instanceof Number
                    && ((Number) args[0]).floatValue() > 0f) {
                args[0] = RecentsBlurPolicy.ratioFromPercent(liveBlurPercent);
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
                if (liveDisableDimming && Boolean.TRUE.equals(RECENTS_DIMMING_SCOPE.get())
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

        // Each semantic factory is optional in a vendor R8 build. Missing
        // one signature must not prevent all the remaining factories from
        // retaining their own dimming suppression on this Launcher version.
        int scopedFactories = 0;
        if (tryInstallDimmingFactoryScope(loader, "getRecentStateParams")) scopedFactories++;
        if (tryInstallDimmingFactoryScope(loader, "getHomeHoldParams")) scopedFactories++;
        if (tryInstallDimmingFactoryScope(
                loader, "getRecentDragParams", float.class)) scopedFactories++;
        if (tryInstallDimmingFactoryScope(loader, "getAppMoveStateParams",
                float.class, boolean.class, boolean.class)) scopedFactories++;
        MainHook.log(TAG + " RecentBlurParams scoped factories installed="
                + scopedFactories + "/4, constructors=" + hookedConstructors);
    }

    /** Direct Recents task-transition SurfaceControl dim, distinct from
     * RecentBlurParams.dimming/ezDimming and from folder-opening blur.
     *
     * Always invoke the native dim method with a zero alpha when disabled:
     * skipping it altogether can leave a previously created dim Surface
     * visible across a cancellation or return-to-folder spring. Do not hook
     * global Window dimming or getUseRecentBlurAnim(), as the latter also
     * removes native blur animation elements and changes their timing.
     */
    private static void installRecentsDimLayerOverride(ClassLoader loader) throws Exception {
        Class<?> layer = Class.forName(RECENTS_DIM_LAYER, false, loader);
        int hooked = 0;
        // The OEM may ship a different SurfaceControlCompat class or the native
        // SurfaceControl overload. Resolve ONLY the typed Recents dim(float,
        // SurfaceControl, boolean) entry, not arbitrary same-name methods.
        for (Method method : layer.getDeclaredMethods()) {
            if (!RecentsDimLayerMethodPolicy.isDimMethod(
                    method.getName(), method.getParameterTypes())) continue;
            method.setAccessible(true);
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (liveDisableDimming && args.length == 3
                        && args[0] instanceof Number) {
                    float nativeAmount = ((Number) args[0]).floatValue();
                    // Always execute the OEM method with zero, including a
                    // cancellation/return clear; skipping it strands a dim layer.
                    args[0] = RecentsBlurPolicy.resolveDimming(nativeAmount, true);
                    if (MainHook.debugLogging && nativeAmount > 0f
                            && dimLayerSuppressLogCount.getAndIncrement() < 8) {
                        MainHook.log(TAG + " Recents DimLayer suppressed native="
                                + nativeAmount);
                    }
                }
                return chain.proceed(args);
            });
            hooked++;
            MainHook.log(TAG + " Recents task-transition dim hooked="
                    + method.toGenericString());
        }
        if (hooked == 0) {
            throw new NoSuchMethodException("No supported " + RECENTS_DIM_LAYER
                    + ".dim(float, SurfaceControl, boolean) overload");
        }
    }

    private static void logDimmingCoverage() {
        MainHook.log(TAG + " wallpaper dimming optOut=" + liveDisableDimming
                + " RecentBlurParams=" + recentParamsInstalled
                + " DimLayer=" + directDimInstalled
                + (liveDisableDimming && (!recentParamsInstalled || !directDimInstalled)
                    ? " PARTIAL: native dim may remain" : ""));
    }

    private static boolean tryInstallDimmingFactoryScope(
            ClassLoader loader, String methodName, Object... parameterTypes) {
        try {
            installDimmingFactoryScope(loader, methodName, parameterTypes);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " RecentBlurParams factory unavailable "
                    + methodName + ": " + error);
            return false;
        }
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
            ClassLoader loader, String methodName) {
        HookUtil.hookMethod(loader, BLUR_UTILS, methodName, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            if (args.length > 1 && args[1] instanceof Number) {
                float nativeRatio = ((Number) args[1]).floatValue();
                // This native value is a blur-strength/motion signal, NOT a
                // reliable Launcher root-scale progress fraction. Use it only
                // to isolate the already-expanded folder material before the
                // vendor scales the root (or undo isolation after cancellation).
                // Sample before the GUI modifies blur strength.
                OpenFolderGlassCoordinator.onNativeRecentsGestureProgress(nativeRatio);
                args[1] = RecentsBlurPolicy.scaleGestureRatio(nativeRatio, liveBlurPercent);
            }
            return chain.proceed(args);
        }, "com.miui.home.launcher.Launcher", float.class, boolean.class);
    }
}
