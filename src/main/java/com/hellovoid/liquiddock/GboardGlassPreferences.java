package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;

/** Gboard-only persisted glass controls. Missing appearance values inherit global Prismal values. */
final class GboardGlassPreferences {
    static final String ENABLED_KEY = ConfigSchema.Gboard.ENABLED.name();
    static final String BLUR_KEY = ConfigSchema.Gboard.BLUR.name();
    static final String TINT_RED_KEY = ConfigSchema.Gboard.TINT_RED.name();
    static final String TINT_GREEN_KEY = ConfigSchema.Gboard.TINT_GREEN.name();
    static final String TINT_BLUE_KEY = ConfigSchema.Gboard.TINT_BLUE.name();
    static final String TINT_ALPHA_KEY = ConfigSchema.Gboard.TINT_ALPHA.name();
    static final boolean ENABLED_DEFAULT = ConfigSchema.Gboard.ENABLED.uiDefault();

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
