package com.hellovoid.liquiddock;

/** Dedicated opt-in switch for the Launcher 4.50 side-slide Sidebar extension. */
final class SideSlideHoldFeatureConfig {
    static final String KEY = "launcher450_side_slide_hold";
    static final boolean DEFAULT_ENABLED = false;

    private SideSlideHoldFeatureConfig() {}

    static boolean isEnabled(ConfigReader reader) {
        ConfigReader effective = reader != null ? reader : ConfigReader.load();
        return effective.b(KEY, DEFAULT_ENABLED);
    }
}
