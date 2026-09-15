package com.hellovoid.liquiddock;

/** Gboard-only persisted glass controls. Missing appearance values inherit global Prismal values. */
final class GboardGlassPreferences {
    static final String ENABLED_KEY = "liquid_gboard_floating_glass";
    static final String BLUR_KEY = "liquid_gboard_blur";
    static final String TINT_RED_KEY = "liquid_gboard_tint_r";
    static final String TINT_GREEN_KEY = "liquid_gboard_tint_g";
    static final String TINT_BLUE_KEY = "liquid_gboard_tint_b";
    static final String TINT_ALPHA_KEY = "liquid_gboard_tint_alpha";
    static final boolean ENABLED_DEFAULT = true;

    static final class Appearance {
        final boolean enabled;
        final boolean hasAppearanceOverride;
        final float blur;
        final int tintR;
        final int tintG;
        final int tintB;
        final int tintAlpha;

        Appearance(
                boolean enabled,
                boolean hasAppearanceOverride,
                float blur,
                int tintR,
                int tintG,
                int tintB,
                int tintAlpha) {
            this.enabled = enabled;
            this.hasAppearanceOverride = hasAppearanceOverride;
            this.blur = Math.max(0f, blur);
            this.tintR = channel(tintR);
            this.tintG = channel(tintG);
            this.tintB = channel(tintB);
            this.tintAlpha = channel(tintAlpha);
        }
    }

    private GboardGlassPreferences() {}

    static Appearance resolve(ConfigReader reader, LiquidDockConfig.Glass base) {
        if (reader == null) reader = ConfigReader.load();
        float baseBlur = base != null ? base.blur : 0f;
        int baseR = base != null ? base.tintR : 255;
        int baseG = base != null ? base.tintG : 255;
        int baseB = base != null ? base.tintB : 255;
        int baseAlpha = base != null ? base.tintAlpha : 35;
        boolean anyOverride = reader.has(BLUR_KEY)
                || reader.has(TINT_RED_KEY)
                || reader.has(TINT_GREEN_KEY)
                || reader.has(TINT_BLUE_KEY)
                || reader.has(TINT_ALPHA_KEY);
        return new Appearance(
                reader.b(ENABLED_KEY, ENABLED_DEFAULT),
                anyOverride,
                reader.f(BLUR_KEY, baseBlur),
                reader.i(TINT_RED_KEY, baseR),
                reader.i(TINT_GREEN_KEY, baseG),
                reader.i(TINT_BLUE_KEY, baseB),
                reader.i(TINT_ALPHA_KEY, baseAlpha));
    }

    private static int channel(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
