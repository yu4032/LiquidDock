package com.hellovoid.prismal;

/** Compares the actual GPU kernel, including downsampling and its minimum radius. */
final class PrismalBlurReusePolicy {
    private PrismalBlurReusePolicy() {}

    static float sigma(float radius, int physicalWidth, int physicalHeight,
                       int logicalWidth, int logicalHeight, float blurScale) {
        float sx = physicalWidth / (float) Math.max(1, logicalWidth);
        float sy = physicalHeight / (float) Math.max(1, logicalHeight);
        float scale = Math.max(0.0001f, Math.min(sx, sy));
        return Math.max(radius * scale * blurScale, 0.5f);
    }

    static boolean needsReblur(float preparedSigma, float requestedSigma) {
        return Float.compare(preparedSigma, requestedSigma) != 0;
    }
}
