package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.prismal.PrismalHighlightProfile;

/** Sidebar-only IF_PRESENT appearance; existing Security Center enable key remains authoritative. */
final class SecurityCenterGlassPreferences {
    static final String PROFILE_ID = "sidebar";
    static final String[] BACKGROUND_FIELDS = {
            "blur", "tint_r", "tint_g", "tint_b", "tint_alpha",
            "corner_radius_dp", "capture_scale_percent", "render_fps"
    };
    static final String[] HIGHLIGHT_FIELDS = {
            "highlight_sky_haze", "highlight_caustics", "highlight_press_glow"
    };

    private SecurityCenterGlassPreferences() {}

    static ThirdPartyGlassAppearance resolve(ConfigReader reader, LiquidDockConfig.Glass base) {
        ConfigReader settings = reader != null ? reader : ConfigReader.load();
        boolean enabled = base != null && base.enabled && base.securityCenterEnabled
                && settings.b(ConfigSchema.Core.ENABLED.name(),
                        ConfigSchema.Core.ENABLED.runtimeFallback());
        ThirdPartyGlassAppearance shared = ThirdPartyGlassProfiles.resolve(
                settings, PROFILE_ID, base,
                new ThirdPartyGlassProfiles.Defaults(enabled, false, -1f));
        PrismalHighlightProfile h = base != null
                ? base.largeSurfaceHighlightProfile : PrismalHighlightProfile.ALL_ENABLED;
        // Preserve every globally configured highlight family, not just the three
        // exposed sidebar switches. ThirdPartyGlassProfiles intentionally restricts
        // generic adapter highlights; Security Center needs the full Prismal family.
        PrismalHighlightProfile highlights = new PrismalHighlightProfile(
                shared.highlightProfile.skyHaze, h.specular, h.litRim, h.oppositeRim,
                h.cornerRim, h.faceSheen, h.plainHighlight,
                shared.highlightProfile.caustics, shared.highlightProfile.pressGlow);
        if (h.os4Edge) highlights = highlights.withOs4EdgeReplacingLegacyEdge();
        return new ThirdPartyGlassAppearance(
                enabled, shared.hasAppearanceOverride, shared.blur,
                shared.tintR, shared.tintG, shared.tintB, shared.tintAlpha,
                shared.captureScalePercent, shared.renderFps,
                shared.cornerRadiusOverrideDp, highlights, false);
    }
}
