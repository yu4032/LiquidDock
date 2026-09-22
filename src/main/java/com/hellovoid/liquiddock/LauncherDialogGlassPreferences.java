package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalParams;

/** Dialog-only appearance and dim policy layered over the shared Launcher Prismal material. */
final class LauncherDialogGlassPreferences {
    static final String BLUR_KEY = ConfigSchema.Glass.DIALOG_BLUR.name();
    static final String TINT_RED_KEY = ConfigSchema.Glass.DIALOG_TINT_RED.name();
    static final String TINT_GREEN_KEY = ConfigSchema.Glass.DIALOG_TINT_GREEN.name();
    static final String TINT_BLUE_KEY = ConfigSchema.Glass.DIALOG_TINT_BLUE.name();
    static final String TINT_ALPHA_KEY = ConfigSchema.Glass.DIALOG_TINT_ALPHA.name();

    static final class Appearance {
        final boolean disableDimming;
        final boolean hasAppearanceOverride;
        final float blur;
        final int tintR;
        final int tintG;
        final int tintB;
        final int tintAlpha;

        Appearance(
                boolean disableDimming,
                boolean hasAppearanceOverride,
                float blur,
                int tintR,
                int tintG,
                int tintB,
                int tintAlpha) {
            this.disableDimming = disableDimming;
            this.hasAppearanceOverride = hasAppearanceOverride;
            this.blur = Math.max(0f, blur);
            this.tintR = channel(tintR);
            this.tintG = channel(tintG);
            this.tintB = channel(tintB);
            this.tintAlpha = channel(tintAlpha);
        }
    }

    private LauncherDialogGlassPreferences() {}

    static Appearance resolve(ConfigReader reader, LiquidDockConfig.Glass base) {
        if (reader == null) reader = ConfigReader.load();
        float baseBlur = base != null ? base.blur : 0f;
        int baseR = base != null ? base.tintR : 255;
        int baseG = base != null ? base.tintG : 255;
        int baseB = base != null ? base.tintB : 255;
        int baseA = base != null ? base.tintAlpha : 0;
        boolean hasAppearanceOverride = reader.has(BLUR_KEY)
                || reader.has(TINT_RED_KEY)
                || reader.has(TINT_GREEN_KEY)
                || reader.has(TINT_BLUE_KEY)
                || reader.has(TINT_ALPHA_KEY);
        return new Appearance(
                reader.b(
                        ConfigSchema.Glass.DIALOG_DISABLE_DIMMING.name(),
                        ConfigSchema.Glass.DIALOG_DISABLE_DIMMING.runtimeFallback()),
                hasAppearanceOverride,
                reader.has(BLUR_KEY) ? reader.f(BLUR_KEY, baseBlur) : baseBlur,
                reader.has(TINT_RED_KEY) ? reader.i(TINT_RED_KEY, baseR) : baseR,
                reader.has(TINT_GREEN_KEY) ? reader.i(TINT_GREEN_KEY, baseG) : baseG,
                reader.has(TINT_BLUE_KEY) ? reader.i(TINT_BLUE_KEY, baseB) : baseB,
                reader.has(TINT_ALPHA_KEY) ? reader.i(TINT_ALPHA_KEY, baseA) : baseA);
    }

    static PrismalParams material(
            LiquidDockConfig.Glass base,
            Appearance appearance,
            float density,
            float dimAmount) {
        PrismalParams source = Miuix307PrismalAdapter.toPortable(
                Miuix307PrismalMaterial.fromConfig(base, density));
        Appearance resolved = appearance != null ? appearance : resolve(null, base);
        PrismalParams.Builder out = PrismalParams.builder(source);
        out.blurRadiusPx = resolved.blur;
        out.tintR = resolved.tintR / 255f;
        out.tintG = resolved.tintG / 255f;
        out.tintB = resolved.tintB / 255f;
        out.tintA = resolved.tintAlpha / 255f;

        // WindowManager's DIM_BEHIND surface sits outside the dialog ViewRoot and is therefore not
        // present in PassBlur's dialog-root source. Apply the same attenuation to the glass-local
        // backdrop so pixels inside and outside the panel observe one visual dim authority.
        float safeDim = clamp01(dimAmount);
        out.brightness = source.brightness * (1f - safeDim);
        return out.build();
    }

    private static int channel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
