package com.hellovoid.liquiddock;

/** Process-local material policy, read at supported lifecycle events. */
final class NativeMaterialMode {
    private NativeMaterialMode() {}
    static boolean isEnabled() {
        LiquidDockConfig config = LiquidDockConfig.from(ConfigReader.load());
        return config.enabled && config.glass.advancedMaterialEnabled;
    }
    static boolean continuousCorners() {
        return ConfigReader.load().b(
                com.hellovoid.liquiddock.config.ConfigSchema.Glass.CONTINUOUS_CORNERS.name(), false);
    }
}
