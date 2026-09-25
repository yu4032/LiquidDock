package com.hellovoid.liquiddock;

/** Dedicated preference contract for the experimental Launcher 4.50 SideSlideHold port. */
final class SideSlideHoldFeatureConfig {
    static final String KEY = "launcher450_side_slide_hold";
    static final boolean DEFAULT_ENABLED = false;

    private SideSlideHoldFeatureConfig() {}

    static boolean isEnabled(ConfigReader reader) {
        ConfigReader effective = reader != null ? reader : ConfigReader.load();
        return effective.b(KEY, DEFAULT_ENABLED);
    }
}
