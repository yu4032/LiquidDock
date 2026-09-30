package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

/**
 * Stops HyperOS from re-enabling compositor blur on the HotSeats material after LiquidDock has
 * taken visual ownership. Positive vendor writes are acknowledged without invoking the hidden
 * View setter, so Launcher and LiquidDock never fight the same View state or invalidate it per
 * frame. Vendor disable writes and every unrelated View still pass through unchanged.
 */
final class LauncherVendorBlurWriteSuppressor {
    private static final String TAG = "[DC][MG]";
    private static boolean installed;

    private LauncherVendorBlurWriteSuppressor() {}

    static synchronized boolean install() {
        if (installed) return true;

        int hooked = 0;
        hooked += hookBoolean("setPassWindowBlurEnabled") ? 1 : 0;
        hooked += hookInt("setMiViewBlurMode") ? 1 : 0;
        hooked += hookInt("setMiBackgroundBlurMode") ? 1 : 0;
        hooked += hookInt("setMiBackgroundBlurRadius") ? 1 : 0;

        installed = hooked > 0;
        if (installed) {
            MainHook.log(TAG + " vendor blur write suppression installed methods=" + hooked);
        } else {
            MainHook.log(TAG + " vendor blur write suppression unavailable");
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
                if (receiver instanceof View && args.length == 1 && args[0] instanceof Number) {
                    boolean owned = MiuixGlassHook.ownsVendorBlurState((View) receiver);
                    if (LauncherVendorBlurWritePolicy.shouldSuppressPositiveBlurWrite(
                            owned, ((Number) args[0]).intValue())) {
                        return successfulSuppressionResult(method);
                    }
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
