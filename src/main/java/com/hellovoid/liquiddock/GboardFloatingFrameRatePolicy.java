package com.hellovoid.liquiddock;

/** Selects the presentation cadence requested from the Gboard glass output Surface. */
final class GboardFloatingFrameRatePolicy {
    private static final float FALLBACK_HZ = 60f;

    private GboardFloatingFrameRatePolicy() {}

    static float preferredHz(float displayHz) {
        return Float.isFinite(displayHz) && displayHz > 0f ? displayHz : FALLBACK_HZ;
    }
}
