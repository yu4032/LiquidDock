package com.hellovoid.liquiddock;

/** Startup-only fallback for the OEM Dock before its native corner radius is committed. */
final class NativeDockCornerPolicy {
    private NativeDockCornerPolicy() {}

    static float provisionalRadius(int width, int height, boolean workstationActive) {
        if (workstationActive || width <= 0 || height <= 0) return Float.NaN;
        return Math.min(width, height) * .5f;
    }
}
