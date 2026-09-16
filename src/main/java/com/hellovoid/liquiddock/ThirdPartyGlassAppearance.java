package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalHighlightProfile;

/** Immutable resolved runtime controls shared by code-registered third-party glass adapters. */
final class ThirdPartyGlassAppearance {
    final boolean enabled;
    final boolean hasAppearanceOverride;
    final float blur;
    final int tintR;
    final int tintG;
    final int tintB;
    final int tintAlpha;
    final int captureScalePercent;
    final int renderFps;
    final float cornerRadiusOverrideDp;
    final PrismalHighlightProfile highlightProfile;
    final boolean freshOnResume;

    ThirdPartyGlassAppearance(
            boolean enabled,
            boolean hasAppearanceOverride,
            float blur,
            int tintR,
            int tintG,
            int tintB,
            int tintAlpha,
            int captureScalePercent,
            int renderFps,
            float cornerRadiusOverrideDp,
            PrismalHighlightProfile highlightProfile,
            boolean freshOnResume) {
        this.enabled = enabled;
        this.hasAppearanceOverride = hasAppearanceOverride;
        this.blur = Math.max(0f, blur);
        this.tintR = channel(tintR);
        this.tintG = channel(tintG);
        this.tintB = channel(tintB);
        this.tintAlpha = channel(tintAlpha);
        this.captureScalePercent = clamp(
                captureScalePercent,
                PassBlurQualityPolicy.MIN_CAPTURE_SCALE_PERCENT,
                PassBlurQualityPolicy.MAX_CAPTURE_SCALE_PERCENT);
        this.renderFps = clamp(renderFps, 0, PassBlurQualityPolicy.MAX_RENDER_FPS);
        this.cornerRadiusOverrideDp = cornerRadiusOverrideDp;
        this.highlightProfile = highlightProfile != null
                ? highlightProfile : PrismalHighlightProfile.ALL_ENABLED;
        this.freshOnResume = freshOnResume;
    }

    private static int channel(int value) {
        return clamp(value, 0, 255);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
