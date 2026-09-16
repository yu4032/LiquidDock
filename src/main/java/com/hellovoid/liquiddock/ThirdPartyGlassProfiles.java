package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalHighlightProfile;

import java.util.regex.Pattern;

/** Resolves bounded, namespaced configuration for code-registered third-party adapters. */
final class ThirdPartyGlassProfiles {
    static final class Defaults {
        final boolean enabled;
        final boolean freshOnResume;
        final float cornerRadiusOverrideDp;

        Defaults(boolean enabled, boolean freshOnResume, float cornerRadiusOverrideDp) {
            this.enabled = enabled;
            this.freshOnResume = freshOnResume;
            this.cornerRadiusOverrideDp = cornerRadiusOverrideDp;
        }
    }

    private static final Pattern PROFILE_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final String PREFIX = "third_party_glass.";

    private ThirdPartyGlassProfiles() {}

    static ThirdPartyGlassAppearance resolve(
            ConfigReader reader,
            String profileId,
            LiquidDockConfig.Glass base,
            Defaults defaults) {
        if (reader == null) reader = ConfigReader.load();
        if (defaults == null) defaults = new Defaults(false, false, -1f);
        validateProfileId(profileId);

        float baseBlur = base != null ? base.blur : 0f;
        int baseR = base != null ? base.tintR : 255;
        int baseG = base != null ? base.tintG : 255;
        int baseB = base != null ? base.tintB : 255;
        int baseAlpha = base != null ? base.tintAlpha : 35;
        int baseScale = base != null
                ? base.passBlurCaptureScalePercent
                : PassBlurQualityPolicy.DEFAULT_CAPTURE_SCALE_PERCENT;
        int baseFps = base != null
                ? base.passBlurRenderFps
                : PassBlurQualityPolicy.DEFAULT_RENDER_FPS;
        PrismalHighlightProfile baseHighlights = base != null && base.largeSurfaceHighlightProfile != null
                ? base.largeSurfaceHighlightProfile : PrismalHighlightProfile.ALL_ENABLED;

        String blur = key(profileId, "blur");
        String tintR = key(profileId, "tint_r");
        String tintG = key(profileId, "tint_g");
        String tintB = key(profileId, "tint_b");
        String tintAlpha = key(profileId, "tint_alpha");
        boolean hasAppearanceOverride = reader.has(blur)
                || reader.has(tintR)
                || reader.has(tintG)
                || reader.has(tintB)
                || reader.has(tintAlpha);

        PrismalHighlightProfile highlights = new PrismalHighlightProfile(
                reader.b(key(profileId, "highlight_sky_haze"), baseHighlights.skyHaze),
                reader.b(key(profileId, "highlight_specular"), baseHighlights.specular),
                reader.b(key(profileId, "highlight_lit_rim"), baseHighlights.litRim),
                reader.b(key(profileId, "highlight_opposite_rim"), baseHighlights.oppositeRim),
                reader.b(key(profileId, "highlight_corner_rim"), baseHighlights.cornerRim),
                reader.b(key(profileId, "highlight_face_sheen"), baseHighlights.faceSheen),
                reader.b(key(profileId, "highlight_plain"), baseHighlights.plainHighlight),
                reader.b(key(profileId, "highlight_caustics"), baseHighlights.caustics),
                reader.b(key(profileId, "highlight_press_glow"), baseHighlights.pressGlow));

        return new ThirdPartyGlassAppearance(
                reader.b(key(profileId, "enabled"), defaults.enabled),
                hasAppearanceOverride,
                reader.f(blur, baseBlur),
                reader.i(tintR, baseR),
                reader.i(tintG, baseG),
                reader.i(tintB, baseB),
                reader.i(tintAlpha, baseAlpha),
                reader.i(key(profileId, "capture_scale_percent"), baseScale),
                reader.i(key(profileId, "render_fps"), baseFps),
                reader.f(key(profileId, "corner_radius_dp"), defaults.cornerRadiusOverrideDp),
                highlights,
                reader.b(key(profileId, "fresh_on_resume"), defaults.freshOnResume));
    }

    static String key(String profileId, String field) {
        validateProfileId(profileId);
        if (field == null || !PROFILE_ID.matcher(field).matches()) {
            throw new IllegalArgumentException("invalid profile field: " + field);
        }
        return PREFIX + profileId + "." + field;
    }

    static void validateProfileId(String profileId) {
        if (profileId == null || !PROFILE_ID.matcher(profileId).matches()) {
            throw new IllegalArgumentException("invalid third-party glass profile id: " + profileId);
        }
    }
}
