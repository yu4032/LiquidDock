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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
