package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.graphics.Point;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Security Center material policy with custom shader as the active Video/Global path. */
final class SecurityCenterMaterialModePolicy {
    private static final int ADVANCED_BLUR_RADIUS_PX = 100;

    private static volatile boolean advancedPresentationFailed;
    private static WeakReference<Object> lifecycleOwner = new WeakReference<>(null);
    private static WeakReference<View> advancedTurbo = new WeakReference<>(null);
    private static WeakReference<View> advancedDock = new WeakReference<>(null);

    private SecurityCenterMaterialModePolicy() {}

    /** Video/Global use LiquidDock's Prismal shader; framework material remains a dormant fallback capability. */
    static LiquidBlurMode currentMode() {
        return LiquidBlurMode.SHADER;
    }

    static synchronized boolean prepareBind(Object owner) {
        if (lifecycleOwner.get() != owner) {
            advancedPresentationFailed = false;
            lifecycleOwner = new WeakReference<>(owner);
        }
        if (!canPresentCustom(currentMode(), MiBlurBridge.isPassWindowBlurAvailable())) {
            advancedPresentationFailed = true;
        }
        return !advancedPresentationFailed;
    }

    static boolean useShaderBlur() {
        return true;
    }

    static boolean canPresentCustom(LiquidBlurMode mode, boolean advancedAvailable) {
        return mode != LiquidBlurMode.ADVANCED_MATERIAL || advancedAvailable;
    }

    static boolean shaderBlurEnabled(LiquidBlurMode mode) {
        return mode != LiquidBlurMode.ADVANCED_MATERIAL;
    }

    /** Dormant framework material capability retained for fail-closed compatibility paths. */
    static synchronized boolean configureAdvancedMaterial(View turbo, View dock) {
        if (advancedPresentationFailed || !MiBlurBridge.isPassWindowBlurAvailable()
                || turbo == null || dock == null) {
            advancedPresentationFailed = true;
            return false;
        }

        return SecurityCenterVendorMaterialState.withModuleMutation(() -> {
            clearConfiguredTargetsUnsafe();
            boolean turboReady = MiBlurBridge.setPassWindowBlurEnabled(turbo, true);
            boolean dockReady = turboReady && MiBlurBridge.applyPassWindowBlur(
                    dock, ADVANCED_BLUR_RADIUS_PX, blendColors(dock));
            if (!turboReady || !dockReady) {
                MiBlurBridge.clearPassWindowBlur(turbo);
                MiBlurBridge.clearPassWindowBlur(dock);
                advancedPresentationFailed = true;
                return false;
            }

            advancedTurbo = new WeakReference<>(turbo);
            advancedDock = new WeakReference<>(dock);
            return true;
        });
    }

    static boolean blockCustomPresentation() {
        return advancedPresentationFailed;
    }

    static boolean retainFrameworkDockOnPanelClose() {
        return currentMode() == LiquidBlurMode.ADVANCED_MATERIAL
                && !advancedPresentationFailed && advancedDock.get() != null;
    }

    static synchronized void releaseAdvancedMaterial() {
        SecurityCenterVendorMaterialState.runModuleMutation(
                SecurityCenterMaterialModePolicy::clearConfiguredTargetsUnsafe);
    }

    static synchronized void resetLifecycle() {
        advancedPresentationFailed = false;
        lifecycleOwner = new WeakReference<>(null);
        advancedTurbo = new WeakReference<>(null);
        advancedDock = new WeakReference<>(null);
    }

    private static void clearConfiguredTargetsUnsafe() {
        View turbo = advancedTurbo.get();
        View dock = advancedDock.get();
        if (turbo != null) MiBlurBridge.clearPassWindowBlur(turbo);
        if (dock != null && dock != turbo) MiBlurBridge.clearPassWindowBlur(dock);
        advancedTurbo = new WeakReference<>(null);
        advancedDock = new WeakReference<>(null);
    }

    private static ArrayList<Point> blendColors(View carrier) {
        boolean dark = carrier != null
                && (carrier.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        ArrayList<Point> colors = new ArrayList<>(2);
        if (dark) {
            colors.add(new Point(-8947849, 107));
            colors.add(new Point(-15043840, 106));
        } else {
            colors.add(new Point(-7237231, 107));
            colors.add(new Point(-3343872, 106));
        }
        return colors;
    }
}
