package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalParams;

/** Folder-only optics; never mutate the global Prismal or Dock material. */
final class OpenFolderGlassOptics {
    private OpenFolderGlassOptics() {}

    static PrismalParams apply(PrismalParams base, int blurPx, int ior100,
                               int thicknessDp, int tintAlpha, float density) {
        if (base == null) throw new IllegalArgumentException("base == null");
        PrismalParams.Builder b = PrismalParams.builder(base);
        b.blurRadiusPx = clamp(blurPx, 0, 60);
        b.ior = clamp(ior100, 100, 200) / 100f;
        b.glassThicknessPx = clamp(thicknessDp, 1, 60) * Math.max(0.1f, density);
        b.tintA = clamp(tintAlpha, 0, 255) / 255f;
        return b.build();
    }

    static PrismalParams apply(PrismalParams base, int blurPx, int ior100,
                               int thicknessDp, int tintAlpha,
                               int fillRed, int fillGreen, int fillBlue, int fillAlpha,
                               float density) {
        PrismalParams original = apply(base, blurPx, ior100, thicknessDp, tintAlpha, density);
        PrismalParams.Builder b = PrismalParams.builder(original);
        if (fillRed >= 0) b.baseColorR = clamp(fillRed, 0, 255) / 255f;
        if (fillGreen >= 0) b.baseColorG = clamp(fillGreen, 0, 255) / 255f;
        if (fillBlue >= 0) b.baseColorB = clamp(fillBlue, 0, 255) / 255f;
        if (fillAlpha >= 0) b.baseColorA = clamp(fillAlpha, 0, 255) / 255f;
        return b.build();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
