package com.hellovoid.liquiddock;

import android.view.View;

/** Launcher 4.50 final workstation-exit geometry boundary. */
final class Launcher450DockTransitionSettlement {
    private static final String BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
    private static boolean installed;
    private static int lastDeferredGeneration = Integer.MIN_VALUE;

    private Launcher450DockTransitionSettlement() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        installed = true;
        try {
            // Decompiled 4.50 calls this only for an immediate final update or after its 200 ms
            // width/height animation ends.  It is the vendor final boundary, not an animator hint.
            HookUtil.hookMethod(classLoader, BACKGROUND_CLASS, "updateRoundRect", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof View && settleIfReady((View) owner)) {
                    commitSettledGeometry((View) owner);
                }
                return result;
            }, int.class, int.class, float.class);
            MainHook.log("[DC] Launcher 4.50 Dock settlement boundary=updateRoundRect");
        } catch (Throwable error) {
            MainHook.log("[DC] Launcher 4.50 Dock transition settlement unavailable: " + error);
        }
    }

    static boolean settleIfReady(View background) {
        DockWorkstationVisualTransition state = DockWorkstationVisualTransition.global();
        if (!state.isExiting()) return false;
        int generation = state.generation();
        if (!MiuixGlassHook.hasReadyNativeGeometry(background)) {
            if (lastDeferredGeneration != generation) {
                lastDeferredGeneration = generation;
                MainHook.log("[DC] workstation exit settlement deferred generation=" + generation
                        + " boundary=updateRoundRect radius="
                        + MiuixGlassHook.readNativeOpticsRadius(background)
                        + " size=" + background.getWidth() + "x" + background.getHeight());
            }
            return false;
        }
        if (!state.settleExit(generation)) return false;
        lastDeferredGeneration = Integer.MIN_VALUE;
        MainHook.log("[DC] workstation exit Dock geometry settled generation=" + generation
                + " boundary=updateRoundRect radius="
                + MiuixGlassHook.readNativeOpticsRadius(background)
                + " size=" + background.getWidth() + "x" + background.getHeight());
        return true;
    }

    private static void commitSettledGeometry(View background) {
        try {
            LiquidDockConfig current = LiquidDockConfig.load();
            MiuixGlassHook.syncGeometry(background, current);
            MainHook.syncDockShadow(background, current.dock);
        } catch (Throwable error) {
            MainHook.log("[DC] workstation exit Dock geometry settlement failed: " + error);
        }
    }
}
