package com.hellovoid.liquiddock;

/** Detects changes to a material's complete root-space quad without Android dependencies. */
final class LauncherGlassRootTransformTracker {
    private static final float EPSILON = 0.001f;
    private final float[] last = new float[8];
    private boolean initialized;

    boolean update(float[] points) {
        if (!initialized) {
            initialized = true;
            copyCanonical(points, last);
            return true;
        }
        for (int i = 0; i < last.length; i++) {
            float next = canonical(points, i);
            float previous = last[i];
            boolean same = Float.isNaN(previous)
                    ? Float.isNaN(next)
                    : Float.isFinite(next) && Math.abs(previous - next) < EPSILON;
            if (!same) {
                copyCanonical(points, last);
                return true;
            }
        }
        return false;
    }

    private static void copyCanonical(float[] source, float[] target) {
        for (int i = 0; i < target.length; i++) target[i] = canonical(source, i);
    }

    private static float canonical(float[] source, int index) {
        if (source == null || source.length != 8 || index < 0 || index >= source.length) {
            return Float.NaN;
        }
        float value = source[index];
        return Float.isFinite(value) ? value : Float.NaN;
    }
}
