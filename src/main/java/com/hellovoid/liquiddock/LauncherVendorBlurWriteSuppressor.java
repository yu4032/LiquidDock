package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

/**
 * Stops HyperOS from re-enabling compositor blur on the HotSeats material after LiquidDock has
 * taken visual ownership. Suppression happens at the vendor write boundary instead of from a
 * root-wide pre-draw loop, so Launcher and LiquidDock never fight the same View state per frame.
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
            Method method = View.class.getMethod(methodName, boolean.class);
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object owner = chain.getThisObject();
                if (owner instanceof View && args.length == 1 && args[0] instanceof Boolean) {
                    boolean owned = MiuixGlassHook.ownsVendorBlurState((View) owner);
                    args[0] = LauncherVendorBlurWritePolicy.passWindowBlurEnabled(
                            owned, (Boolean) args[0]);
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
            Method method = View.class.getMethod(methodName, int.class);
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object owner = chain.getThisObject();
                if (owner instanceof View && args.length == 1 && args[0] instanceof Number) {
                    boolean owned = MiuixGlassHook.ownsVendorBlurState((View) owner);
                    args[0] = LauncherVendorBlurWritePolicy.blurModeOrRadius(
                            owned, ((Number) args[0]).intValue());
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
}
