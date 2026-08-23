package com.hellovoid.liquiddock;

/** Detects effective ancestor-visibility transitions without depending on Android View APIs. */
final class LauncherGlassEffectiveVisibilityTracker {
    private static final float EPSILON = 0.001f;

    private boolean initialized;
    private float lastAlpha;

    boolean update(float effectiveAlpha) {
        float next = Float.isFinite(effectiveAlpha) ? effectiveAlpha : Float.NaN;
        if (!initialized) {
            initialized = true;
            lastAlpha = next;
            return true;
        }
        boolean same = Float.isNaN(lastAlpha)
                ? Float.isNaN(next)
                : Float.isFinite(next) && Math.abs(lastAlpha - next) < EPSILON;
        if (same) return false;
        lastAlpha = next;
        return true;
    }
}
