package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalHighlightProfile;

/** Gboard persisted controls layered over the shared third-party glass profile contract. */
final class GboardGlassPreferences {
    static final String PROFILE_ID = "gboard.floating";
    static final String ENABLED_KEY = ConfigSchema.Gboard.ENABLED.name();
    static final String AUTO_RESIZE_AFTER_HANDLE_DRAG_KEY =
            "liquid_gboard_auto_resize_after_handle_drag";
    static final String BLUR_KEY = ConfigSchema.Gboard.BLUR.name();
    static final String TINT_RED_KEY = ConfigSchema.Gboard.TINT_RED.name();
    static final String TINT_GREEN_KEY = ConfigSchema.Gboard.TINT_GREEN.name();
    static final String TINT_BLUE_KEY = ConfigSchema.Gboard.TINT_BLUE.name();
    static final String TINT_ALPHA_KEY = ConfigSchema.Gboard.TINT_ALPHA.name();
    static final boolean ENABLED_DEFAULT = ConfigSchema.Gboard.ENABLED.uiDefault();
    static final boolean AUTO_RESIZE_AFTER_HANDLE_DRAG_DEFAULT = true;

    /** Compatibility view retained for existing Gboard hooks/tests. */
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

        ThirdPartyGlassAppearance toShared(LiquidDockConfig.Glass base) {
            int scale = base != null
                    ? base.passBlurCaptureScalePercent
                    : PassBlurQualityPolicy.DEFAULT_CAPTURE_SCALE_PERCENT;
            int fps = base != null
                    ? base.passBlurRenderFps
                    : PassBlurQualityPolicy.DEFAULT_RENDER_FPS;
            PrismalHighlightProfile highlights = base != null && base.largeSurfaceHighlightProfile != null
                    ? base.largeSurfaceHighlightProfile : PrismalHighlightProfile.ALL_ENABLED;
            return new ThirdPartyGlassAppearance(
                    enabled, hasAppearanceOverride, blur,
                    tintR, tintG, tintB, tintAlpha,
                    scale, fps, -1f, highlights, false);
        }
    }

    private GboardGlassPreferences() {}

    static Appearance resolve(ConfigReader reader, LiquidDockConfig.Glass base) {
        ThirdPartyGlassAppearance shared = resolveShared(reader, base);
        return new Appearance(
                shared.enabled,
                shared.hasAppearanceOverride,
                shared.blur,
                shared.tintR,
                shared.tintG,
                shared.tintB,
                shared.tintAlpha);
    }

    static ThirdPartyGlassAppearance resolveShared(ConfigReader reader, LiquidDockConfig.Glass base) {
        if (reader == null) reader = ConfigReader.load();
        ThirdPartyGlassAppearance shared = ThirdPartyGlassProfiles.resolve(
                reader,
                PROFILE_ID,
                base,
                new ThirdPartyGlassProfiles.Defaults(ENABLED_DEFAULT, false, -1f));
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
                shared.highlightProfile,
                shared.freshOnResume);
    }

    private static int channel(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
