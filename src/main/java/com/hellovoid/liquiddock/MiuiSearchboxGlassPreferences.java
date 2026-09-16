package com.hellovoid.liquiddock;

/** Persisted Searchbox controls plus hidden shared-profile overrides. */
final class MiuiSearchboxGlassPreferences {
    static final String PROFILE_ID = "miui.searchbox";
    static final String ENABLED_KEY = "liquid_miui_searchbox_glass";
    static final String BLUR_KEY = "liquid_miui_searchbox_blur";
    static final String TINT_RED_KEY = "liquid_miui_searchbox_tint_r";
    static final String TINT_GREEN_KEY = "liquid_miui_searchbox_tint_g";
    static final String TINT_BLUE_KEY = "liquid_miui_searchbox_tint_b";
    static final String TINT_ALPHA_KEY = "liquid_miui_searchbox_tint_alpha";
    static final boolean ENABLED_DEFAULT = true;

    private MiuiSearchboxGlassPreferences() {}

    static boolean isEnabled(ConfigReader reader) {
        return resolve(reader, LiquidDockConfig.from(reader != null ? reader : ConfigReader.load()).glass).enabled;
    }

    static ThirdPartyGlassAppearance resolve(ConfigReader reader, LiquidDockConfig.Glass base) {
        if (reader == null) reader = ConfigReader.load();
        ThirdPartyGlassAppearance shared = ThirdPartyGlassProfiles.resolve(
                reader,
                PROFILE_ID,
                base,
                new ThirdPartyGlassProfiles.Defaults(ENABLED_DEFAULT, true, -1f));
        boolean publicAppearance = reader.has(BLUR_KEY)
                || reader.has(TINT_RED_KEY)
                || reader.has(TINT_GREEN_KEY)
                || reader.has(TINT_BLUE_KEY)
                || reader.has(TINT_ALPHA_KEY);
        return new ThirdPartyGlassAppearance(
                reader.has(ENABLED_KEY)
                        ? reader.b(ENABLED_KEY, ENABLED_DEFAULT)
                        : shared.enabled,
                shared.hasAppearanceOverride || publicAppearance,
                reader.f(BLUR_KEY, shared.blur),
                reader.i(TINT_RED_KEY, shared.tintR),
                reader.i(TINT_GREEN_KEY, shared.tintG),
                reader.i(TINT_BLUE_KEY, shared.tintB),
                reader.i(TINT_ALPHA_KEY, shared.tintAlpha),
                shared.captureScalePercent,
                shared.renderFps,
                shared.cornerRadiusOverrideDp,
                shared.freshOnResume);
    }
}
