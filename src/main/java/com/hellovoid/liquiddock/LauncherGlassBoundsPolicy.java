package com.hellovoid.liquiddock;

/** Symmetric edge expansion/inset for component glass bounds. */
final class LauncherGlassBoundsPolicy {
    private LauncherGlassBoundsPolicy() {}

    static float[] apply(float left, float top, float right, float bottom, float offsetPx) {
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
        return new float[]{nextLeft, nextTop, nextRight, nextBottom};
    }

    static float capRadius(float radiusPx, float width, float height) {
        if (!Float.isFinite(radiusPx)) return 0f;
        return Math.max(0f, Math.min(radiusPx, Math.max(0f, Math.min(width, height) * 0.5f)));
    }
}
