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
        b.tintA = clamp(tintAlpha, 0, 160) / 255f;
        return b.build();
    }

    /** Prebuild a small per-session table: no object allocations or Gaussian
     * passes at 165 Hz. The expanded folder and resident desktop glass share
     * all fields except the four folder-only overrides.
     */
    static PrismalParams[] closeToWorkspace(PrismalParams folder, PrismalParams workspace) {
        PrismalParams[] steps = new PrismalParams[65];
        for (int i = 0; i < steps.length; i++) {
            if (i == 0) { steps[i] = folder; continue; }
            if (i == steps.length - 1) { steps[i] = workspace; continue; }
            float t = i / (float) (steps.length - 1);
            PrismalParams.Builder b = PrismalParams.builder(folder);
            b.ior = interpolate(folder.ior, workspace.ior, t);
            b.glassThicknessPx = interpolate(folder.glassThicknessPx, workspace.glassThicknessPx, t);
            b.tintA = interpolate(folder.tintA, workspace.tintA, t);
            b.blurRadiusPx = interpolate(folder.blurRadiusPx, workspace.blurRadiusPx, t);
            steps[i] = b.build();
        }
        return steps;
    }

    private static float interpolate(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
