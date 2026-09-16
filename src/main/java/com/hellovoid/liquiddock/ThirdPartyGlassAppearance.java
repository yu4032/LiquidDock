package com.hellovoid.liquiddock;

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
            boolean freshOnResume) {
        this.enabled = enabled;
        this.hasAppearanceOverride = hasAppearanceOverride;
        this.blur = Math.max(0f, blur);
        this.tintR = channel(tintR);
        this.tintG = channel(tintG);
        this.tintB = channel(tintB);
        this.tintAlpha = channel(tintAlpha);
        this.captureScalePercent = clamp(captureScalePercent, 1, 100);
        this.renderFps = clamp(renderFps, 1, 120);
        this.cornerRadiusOverrideDp = cornerRadiusOverrideDp;
        this.freshOnResume = freshOnResume;
    }

    private static int channel(int value) {
        return clamp(value, 0, 255);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
