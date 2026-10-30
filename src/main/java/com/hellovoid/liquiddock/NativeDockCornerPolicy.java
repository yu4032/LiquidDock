package com.hellovoid.liquiddock;

/** Startup-only fallback for the OEM Dock before its native corner radius is committed. */
final class NativeDockCornerPolicy {
    private NativeDockCornerPolicy() {}

    /** Never rewrite OEM blur regions which already have their own corner geometry. */
    static boolean hasRectangularStartupCorners(float[] corners) {
        if (corners == null || corners.length != 4) return false;
        for (float corner : corners) {
            if (!Float.isFinite(corner) || corner < 0f || corner > .5f) return false;
        }
        return true;
    }

    static float provisionalRadius(int width, int height, boolean workstationActive) {
        if (workstationActive || width <= 0 || height <= 0) return Float.NaN;
        return Math.min(width, height) * .5f;
    }
}
