package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.graphics.Point;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import com.hellovoid.liquiddock.config.ConfigSchema;

/** Security Center adapter for the persisted normal/advanced LiquidDock material capability. */
final class SecurityCenterMaterialModePolicy {
    private static final int ADVANCED_BLUR_RADIUS_PX = 100;

    private static volatile boolean advancedPresentationFailed;
    private static WeakReference<Object> lifecycleOwner = new WeakReference<>(null);
    private static WeakReference<View> advancedTurbo = new WeakReference<>(null);
    private static WeakReference<View> advancedDock = new WeakReference<>(null);
    private static WeakReference<View> advancedApps = new WeakReference<>(null);

    private SecurityCenterMaterialModePolicy() {}

    static LiquidBlurMode currentMode() {
        ConfigReader config = ConfigReader.load();
        return LiquidBlurMode.fromPersisted(config.s(
                ConfigSchema.Glass.BLUR_MODE.name(),
                ConfigSchema.Glass.BLUR_MODE.runtimeFallback()));
    }

    static synchronized boolean prepareBind(Object owner) {
        LiquidBlurMode mode = currentMode();
        if (mode != LiquidBlurMode.ADVANCED_MATERIAL) {
            advancedPresentationFailed = false;
            lifecycleOwner = new WeakReference<>(owner);
            return true;
        }
        if (lifecycleOwner.get() != owner) {
            advancedPresentationFailed = false;
            lifecycleOwner = new WeakReference<>(owner);
        }
        if (!canPresentCustom(mode, MiBlurBridge.isPassWindowBlurAvailable())) {
            advancedPresentationFailed = true;
        }
        return !advancedPresentationFailed;
    }

    static boolean useShaderBlur() {
        return shaderBlurEnabled(currentMode());
    }

    static boolean canPresentCustom(LiquidBlurMode mode, boolean advancedAvailable) {
        return mode != LiquidBlurMode.ADVANCED_MATERIAL || advancedAvailable;
    }

    static boolean shaderBlurEnabled(LiquidBlurMode mode) {
        return mode != LiquidBlurMode.ADVANCED_MATERIAL;
    }

    /**
     * Applies LiquidDock advanced material only under the explicit module-mutation guard so the
     * stable View-API vendor mirror never mistakes our writes for vendor intent.
     */
    static synchronized boolean configureAdvancedMaterial(View turbo, View dock, View apps) {
        if (currentMode() != LiquidBlurMode.ADVANCED_MATERIAL) return true;
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
            boolean appsReady = apps == null || (dockReady && MiBlurBridge.applyPassWindowBlur(
                    apps, ADVANCED_BLUR_RADIUS_PX, blendColors(apps)));
            if (!turboReady || !dockReady || !appsReady) {
                MiBlurBridge.clearPassWindowBlur(turbo);
                MiBlurBridge.clearPassWindowBlur(dock);
                if (apps != null) MiBlurBridge.clearPassWindowBlur(apps);
                advancedPresentationFailed = true;
                return false;
            }

            advancedTurbo = new WeakReference<>(turbo);
            advancedDock = new WeakReference<>(dock);
            advancedApps = new WeakReference<>(apps);
            return true;
        });
    }

    static boolean blockCustomPresentation() {
        return currentMode() == LiquidBlurMode.ADVANCED_MATERIAL && advancedPresentationFailed;
    }

    /** Clear LiquidDock material before the stable View-API vendor snapshot is replayed. */
    static synchronized void releaseAdvancedMaterial() {
        SecurityCenterVendorMaterialState.runModuleMutation(
                SecurityCenterMaterialModePolicy::clearConfiguredTargetsUnsafe);
    }

    static synchronized void resetLifecycle() {
        advancedPresentationFailed = false;
        lifecycleOwner = new WeakReference<>(null);
        advancedTurbo = new WeakReference<>(null);
        advancedDock = new WeakReference<>(null);
        advancedApps = new WeakReference<>(null);
    }

    private static void clearConfiguredTargetsUnsafe() {
        View turbo = advancedTurbo.get();
        View dock = advancedDock.get();
        View apps = advancedApps.get();
        if (turbo != null) MiBlurBridge.clearPassWindowBlur(turbo);
        if (dock != null && dock != turbo) MiBlurBridge.clearPassWindowBlur(dock);
        if (apps != null && apps != dock && apps != turbo) MiBlurBridge.clearPassWindowBlur(apps);
        advancedTurbo = new WeakReference<>(null);
        advancedDock = new WeakReference<>(null);
        advancedApps = new WeakReference<>(null);
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
