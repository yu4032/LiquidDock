package com.hellovoid.liquiddock;

import android.view.View;

/**
 * Launcher 4.50-specific settlement boundary for workstation -> normal Dock geometry.
 *
 * Launcher 4.50 calls updateRoundRect(width, height, radius) only when the new Dock geometry is
 * ready to become the vendor-visible rounded rectangle. That method is therefore the settlement
 * boundary; private animator fields are implementation details and can finish before the final
 * ordinary-mode geometry is published.
 */
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
            HookUtil.hookMethod(classLoader, BACKGROUND_CLASS, "updateRoundRect", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof View && settleIfReady((View) owner)) {
                    commitSettledGeometry((View) owner);
                }
                return result;
            }, int.class, int.class, float.class);
            MainHook.log("[DC] Launcher 4.50 Dock transition settlement hooks=1"
                    + " boundary=updateRoundRect");
        } catch (Throwable error) {
            MainHook.log("[DC] Launcher 4.50 Dock transition settlement unavailable: " + error);
        }
    }

    /**
     * Returns true only when this call changes the visual phase from EXITING_WORKSTATION to NORMAL.
     * The updateRoundRect hook calls this after the vendor method has accepted its final values.
     */
    static boolean settleIfReady(View background) {
        DockWorkstationVisualTransition state = DockWorkstationVisualTransition.global();
        if (!state.isExiting()) return false;
        final int generation = state.generation();

        if (!MiuixGlassHook.hasReadyNativeGeometry(background)) {
            if (lastDeferredGeneration != generation) {
                lastDeferredGeneration = generation;
                MainHook.log("[DC] workstation exit settlement deferred generation=" + generation
                        + " radius=" + MiuixGlassHook.readNativeOpticsRadius(background)
                        + " size=" + background.getWidth() + "x" + background.getHeight());
            }
            return false;
        }

        if (!state.settleExit(generation)) return false;
        lastDeferredGeneration = Integer.MIN_VALUE;
        MainHook.log("[DC] workstation exit Dock geometry settled generation=" + generation
                + " radius=" + MiuixGlassHook.readNativeOpticsRadius(background)
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
