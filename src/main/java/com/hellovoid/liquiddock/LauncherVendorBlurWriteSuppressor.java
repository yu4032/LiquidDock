package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

/**
 * Stops HyperOS from re-enabling compositor blur on the HotSeats material after LiquidDock has
 * taken visual ownership. LiquidDock keeps the native pass-window gate/modes enabled at radius
 * zero so SurfaceFlinger keeps the wallpaper on the same GPU/client-composition path. Vendor
 * writes cannot drop that compositor hold or make the native blur visible. Explicit LiquidDock
 * writes use a thread-local bypass; unrelated Views always pass through unchanged.
 */
final class LauncherVendorBlurWriteSuppressor {
    private static final String TAG = "[DC][MG]";
    private static boolean attempted;
    private static boolean installed;
    private static final ThreadLocal<Integer> INTERNAL_WRITE_DEPTH =
            ThreadLocal.withInitial(() -> Integer.valueOf(0));

    private LauncherVendorBlurWriteSuppressor() {}

    static void beginInternalWrite() {
        INTERNAL_WRITE_DEPTH.set(Integer.valueOf(INTERNAL_WRITE_DEPTH.get().intValue() + 1));
    }

    static void endInternalWrite() {
        int depth = INTERNAL_WRITE_DEPTH.get().intValue() - 1;
        if (depth <= 0) INTERNAL_WRITE_DEPTH.remove();
        else INTERNAL_WRITE_DEPTH.set(Integer.valueOf(depth));
    }

    private static boolean isInternalWrite() {
        return INTERNAL_WRITE_DEPTH.get().intValue() > 0;
    }

    static synchronized boolean install() {
        if (attempted) return installed;
        attempted = true;

        int hooked = 0;
        hooked += hookBoolean("setPassWindowBlurEnabled") ? 1 : 0;
        hooked += hookInt("setMiViewBlurMode") ? 1 : 0;
        hooked += hookInt("setMiBackgroundBlurMode") ? 1 : 0;
        hooked += hookInt("setMiBackgroundBlurRadius") ? 1 : 0;

        installed = hooked == 4;
        if (installed) {
            MainHook.log(TAG + " vendor blur write suppression installed methods=" + hooked);
        } else {
            MainHook.log(TAG + " vendor blur write suppression incomplete methods=" + hooked);
        }
        return installed;
    }

    private static boolean hookBoolean(String methodName) {
        try {
            Method method = HookUtil.findMethodExact(
                    View.class, methodName, new Class<?>[]{boolean.class});
            HookUtil.hook(method, chain -> {
                Object receiver = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (isInternalWrite()) return chain.proceed(args);
                if (receiver instanceof View && args.length == 1 && args[0] instanceof Boolean) {
                    boolean owned = MiuixGlassHook.ownsVendorBlurState((View) receiver);
                    if (LauncherVendorBlurWritePolicy.shouldSuppressPassWindowWrite(
                            owned, (Boolean) args[0])) {
                        return successfulSuppressionResult(method);
                    }
                }
                return chain.proceed(args);
            });
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " vendor blur boolean write hook unavailable method="
                    + methodName + ": " + error);
            return false;
        }
    }

    private static boolean hookInt(String methodName) {
        try {
            Method method = HookUtil.findMethodExact(
                    View.class, methodName, new Class<?>[]{int.class});
            HookUtil.hook(method, chain -> {
                Object receiver = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (isInternalWrite()) return chain.proceed(args);
                if (receiver instanceof View && args.length == 1 && args[0] instanceof Number) {
                    boolean owned = MiuixGlassHook.ownsVendorBlurState((View) receiver);
                    int requested = ((Number) args[0]).intValue();
                    boolean suppress = "setMiBackgroundBlurRadius".equals(methodName)
                            ? LauncherVendorBlurWritePolicy.shouldSuppressBlurRadiusWrite(
                                    owned, requested)
                            : LauncherVendorBlurWritePolicy.shouldSuppressBlurModeWrite(
                                    owned, requested);
                    if (suppress) return successfulSuppressionResult(method);
                }
                return chain.proceed(args);
            });
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " vendor blur int write hook unavailable method="
                    + methodName + ": " + error);
            return false;
        }
    }

    private static Object successfulSuppressionResult(Method method) {
        Class<?> result = method.getReturnType();
        if (result == boolean.class || result == Boolean.class) return Boolean.TRUE;
        return null;
    }
}
