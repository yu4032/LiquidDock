package com.hellovoid.liquiddock;

import java.lang.ref.WeakReference;

/** Security Center policy for LiquidDock's custom shader-material backend. */
final class SecurityCenterMaterialModePolicy {
    private static WeakReference<Object> lifecycleOwner = new WeakReference<>(null);

    private SecurityCenterMaterialModePolicy() {}

    /** Security Center deliberately uses LiquidDock's own shader glass. */
    static LiquidBlurMode currentMode() {
        return LiquidBlurMode.SHADER;
    }

    static synchronized boolean prepareBind(Object owner) {
        if (lifecycleOwner.get() != owner) {
            lifecycleOwner = new WeakReference<>(owner);
        }
        return true;
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

    static synchronized void resetLifecycle() {
        lifecycleOwner = new WeakReference<>(null);
    }
}
