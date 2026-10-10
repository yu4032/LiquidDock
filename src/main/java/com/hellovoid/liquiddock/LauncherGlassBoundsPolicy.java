package com.hellovoid.liquiddock;

/** Symmetric edge expansion/inset for component glass bounds. */
final class LauncherGlassBoundsPolicy {
    private LauncherGlassBoundsPolicy() {}

    static float[] apply(float left, float top, float right, float bottom, float offsetPx) {
        float[] result = new float[4];
        applyInto(left, top, right, bottom, offsetPx, result);
        return result;
    }

    /**
     * Allocation-free form for UI-thread geometry captures with caller-owned scratch.
     * Writes the four bounds at indices [0..3] without modifying the remaining slots.
     */
    static void applyInto(
            float left, float top, float right, float bottom, float offsetPx, float[] out) {
        if (out == null || out.length < 4) {
            throw new IllegalArgumentException("bounds output must contain at least 4 floats");
        }
        float safeOffset = Float.isFinite(offsetPx) ? offsetPx : 0f;
        float nextLeft = left - safeOffset;
        float nextTop = top - safeOffset;
        float nextRight = right + safeOffset;
        float nextBottom = bottom + safeOffset;
        if (!(nextRight > nextLeft)) {
            float center = (left + right) * 0.5f;
            nextLeft = center - 0.5f;
            nextRight = center + 0.5f;
        }
        if (!(nextBottom > nextTop)) {
            float center = (top + bottom) * 0.5f;
            nextTop = center - 0.5f;
            nextBottom = center + 0.5f;
        }
        out[0] = nextLeft;
        out[1] = nextTop;
        out[2] = nextRight;
        out[3] = nextBottom;
    }

    static float capRadius(float radiusPx, float width, float height) {
        if (!Float.isFinite(radiusPx)) return 0f;
        return Math.max(0f, Math.min(radiusPx, Math.max(0f, Math.min(width, height) * 0.5f)));
    }
}
