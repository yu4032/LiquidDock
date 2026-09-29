package com.hellovoid.liquiddock;

/** Dedicated opt-in switch for the Launcher 4.50 side-slide Sidebar extension. */
final class SideSlideHoldFeatureConfig {
    static final String KEY = "launcher450_side_slide_hold";
    static final boolean DEFAULT_ENABLED = false;

    static final String SECOND_STAGE_DISTANCE_PX_KEY =
            "launcher450_side_slide_second_stage_distance_px";
    static final int DEFAULT_SECOND_STAGE_DISTANCE_PX = 240;
    static final int MIN_SECOND_STAGE_DISTANCE_PX = 180;
    static final int MAX_SECOND_STAGE_DISTANCE_PX = 480;

    private SideSlideHoldFeatureConfig() {}

    static boolean isEnabled(ConfigReader reader) {
        ConfigReader effective = reader != null ? reader : ConfigReader.load();
        return effective.b(KEY, DEFAULT_ENABLED);
    }

    static int secondStageDistancePx(ConfigReader reader) {
        ConfigReader effective = reader != null ? reader : ConfigReader.load();
        return Math.max(
                MIN_SECOND_STAGE_DISTANCE_PX,
                Math.min(
                        effective.i(
                                SECOND_STAGE_DISTANCE_PX_KEY,
                                DEFAULT_SECOND_STAGE_DISTANCE_PX),
                        MAX_SECOND_STAGE_DISTANCE_PX));
    }
}
