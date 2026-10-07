package com.hellovoid.liquiddock;

/** Baidu Input Method controls backed by the shared third-party glass profile contract. */
final class BaiduInputMethodGlassPreferences {
    static final String PROFILE_ID = "baidu.floating";
    static final String ENABLED_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, "enabled");
    static final String BLUR_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, "blur");
    static final String TINT_RED_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, "tint_r");
    static final String TINT_GREEN_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, "tint_g");
    static final String TINT_BLUE_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, "tint_b");
    static final String TINT_ALPHA_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, "tint_alpha");
    static final boolean ENABLED_DEFAULT = true;

    private BaiduInputMethodGlassPreferences() {}

    static ThirdPartyGlassAppearance resolve(ConfigReader reader, LiquidDockConfig.Glass base) {
        return ThirdPartyGlassProfiles.resolve(
                reader,
                PROFILE_ID,
                base,
                new ThirdPartyGlassProfiles.Defaults(ENABLED_DEFAULT, false, -1f));
    }
}
