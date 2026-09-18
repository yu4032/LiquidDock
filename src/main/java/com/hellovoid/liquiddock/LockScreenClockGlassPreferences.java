package com.hellovoid.liquiddock;

/** Bounded settings for the forced OS3 lockscreen clock replacement. */
final class LockScreenClockGlassPreferences {
    static final String PROFILE_ID = "systemui.lockscreen_clock";
    // v2 deliberately does not inherit the first experimental enable bit. The v1 runtime could
    // crash-loop SystemUI; requiring one explicit re-enable after upgrade is the recovery barrier.
    static final String ENABLED_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, "glyph_enabled_v2");
    static final String BLUR_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, "blur");
    static final String TINT_RED_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, "tint_r");
    static final String TINT_GREEN_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, "tint_g");
    static final String TINT_BLUE_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, "tint_b");
    static final String TINT_ALPHA_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, "tint_alpha");
    static final boolean ENABLED_DEFAULT = false;

    private LockScreenClockGlassPreferences() {}

    static ThirdPartyGlassAppearance resolve(ConfigReader reader, LiquidDockConfig.Glass base) {
        if (reader == null) reader = ConfigReader.load();
        ThirdPartyGlassAppearance appearance = ThirdPartyGlassProfiles.resolve(
                reader,
                PROFILE_ID,
                base,
                new ThirdPartyGlassProfiles.Defaults(ENABLED_DEFAULT, true, 0f));
        return new ThirdPartyGlassAppearance(
                reader.b(ENABLED_KEY, ENABLED_DEFAULT),
                appearance.hasAppearanceOverride,
                appearance.blur,
                appearance.tintR,
                appearance.tintG,
                appearance.tintB,
                appearance.tintAlpha,
                appearance.captureScalePercent,
                appearance.renderFps,
                appearance.cornerRadiusOverrideDp,
                appearance.highlightProfile,
                appearance.freshOnResume);
    }
}
