package com.hellovoid.liquiddock;

/** Pure geometry policy shared by Android rendering and host-JVM regression tests. */
final class DockStrokeGeometry {
    private DockStrokeGeometry() {}

    static float[] resolveSquircleRingInsets(
            float strokeWidthPx, float ignoredLegacyOutwardOffsetPx) {
        float safeWidth = Math.max(0f, strokeWidthPx);
        return new float[] {0f, safeWidth * 0.5f};
    }
}
