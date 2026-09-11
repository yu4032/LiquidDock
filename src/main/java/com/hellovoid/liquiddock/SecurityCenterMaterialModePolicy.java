package com.hellovoid.liquiddock;

import android.view.View;

import com.hellovoid.liquiddock.config.ConfigSchema;

/** Security Center adapter for the persisted normal/advanced LiquidDock material capability. */
final class SecurityCenterMaterialModePolicy {
    private static final ThreadLocal<Boolean> ADVANCED_CONSTRUCTION = new ThreadLocal<>();

    private SecurityCenterMaterialModePolicy() {}

    static void bindAssistant(View turbo, View dock, View box, int type) {
        boolean advanced = advancedRequested() && MiBlurBridge.isAvailable();
        ADVANCED_CONSTRUCTION.set(advanced);
        try {
            SecurityCenterGlassRuntimeState.bindAssistant(turbo, dock, box, type);
        } finally {
            ADVANCED_CONSTRUCTION.remove();
        }
    }

    static boolean shaderBlurEnabledForCurrentConstruction() {
        return !Boolean.TRUE.equals(ADVANCED_CONSTRUCTION.get());
    }

    static boolean configureSink(View sink) {
        if (sink == null) return false;
        if (!advancedRequested()) {
            MiBlurBridge.clearContentBlur(sink);
            return false;
        }
        ConfigReader config = ConfigReader.load();
        int radius = Math.max(1, Math.round(config.f(
                ConfigSchema.Glass.BLUR.name(), ConfigSchema.Glass.BLUR.runtimeFallback())));
        return MiBlurBridge.applyContentBlur(sink, radius, 0.5f);
    }

    static void releaseSink(View sink) {
        if (sink != null) MiBlurBridge.clearContentBlur(sink);
    }

    private static boolean advancedRequested() {
        ConfigReader config = ConfigReader.load();
        return LiquidBlurMode.fromPersisted(config.s(
                ConfigSchema.Glass.BLUR_MODE.name(),
                ConfigSchema.Glass.BLUR_MODE.runtimeFallback()))
                == LiquidBlurMode.ADVANCED_MATERIAL;
    }
}
