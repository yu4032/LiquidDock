package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;

import com.hellovoid.liquiddock.config.ConfigSchema;

/** Security Center adapter for the persisted normal/advanced LiquidDock material capability. */
final class SecurityCenterMaterialModePolicy {
    private static volatile boolean advancedPresentationFailed;
    private static WeakReference<Object> lifecycleOwner = new WeakReference<>(null);

    private SecurityCenterMaterialModePolicy() {}

    static LiquidBlurMode currentMode() {
        ConfigReader config = ConfigReader.load();
        return LiquidBlurMode.fromPersisted(config.s(
                ConfigSchema.Glass.BLUR_MODE.name(),
                ConfigSchema.Glass.BLUR_MODE.runtimeFallback()));
    }

    static synchronized boolean prepareBind(Object owner) {
        if (currentMode() != LiquidBlurMode.ADVANCED_MATERIAL) {
            advancedPresentationFailed = false;
            lifecycleOwner = new WeakReference<>(owner);
            return true;
        }
        if (lifecycleOwner.get() != owner) {
            advancedPresentationFailed = false;
            lifecycleOwner = new WeakReference<>(owner);
        }
        if (!MiBlurBridge.isAvailable()) advancedPresentationFailed = true;
        return !advancedPresentationFailed;
    }

    static boolean useShaderBlur() {
        return currentMode() != LiquidBlurMode.ADVANCED_MATERIAL;
    }

    static boolean configureSink(View sink) {
        if (sink == null) return false;
        if (currentMode() != LiquidBlurMode.ADVANCED_MATERIAL) {
            MiBlurBridge.clearContentBlur(sink);
            return true;
        }
        if (advancedPresentationFailed || !MiBlurBridge.isAvailable()) {
            advancedPresentationFailed = true;
            return false;
        }
        ConfigReader config = ConfigReader.load();
        int radius = Math.max(1, Math.round(config.f(
                ConfigSchema.Glass.BLUR.name(), ConfigSchema.Glass.BLUR.runtimeFallback())));
        boolean ready = MiBlurBridge.applyContentBlur(sink, radius, 0.5f);
        if (!ready) advancedPresentationFailed = true;
        return ready;
    }

    static boolean blockCustomPresentation() {
        return currentMode() == LiquidBlurMode.ADVANCED_MATERIAL && advancedPresentationFailed;
    }

    static void releaseSink(View sink) {
        if (sink != null) MiBlurBridge.clearContentBlur(sink);
    }

    static synchronized void resetLifecycle() {
        advancedPresentationFailed = false;
        lifecycleOwner = new WeakReference<>(null);
    }
}
