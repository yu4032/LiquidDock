package com.hellovoid.prismal;

/** Pure sizing policy for renderer-owned local Bloom scratch targets. */
final class PrismalBloomTarget {
    static final class Spec {
        final int width;
        final int height;
        final float paddingPx;

        Spec(int width, int height, float paddingPx) {
            this.width = width;
            this.height = height;
            this.paddingPx = paddingPx;
        }
    }

    static Spec plan(float glassWidth,
                     float glassHeight,
                     float bloomWidthPx,
                     float blurRadiusPx,
                     int outputWidth,
                     int outputHeight) {
        float safeGlassWidth = Math.max(1f, finitePositive(glassWidth));
        float safeGlassHeight = Math.max(1f, finitePositive(glassHeight));
        float safeBloom = finitePositive(bloomWidthPx);
        float safeBlur = finitePositive(blurRadiusPx);
        float padding = safeBloom * 0.5f + safeBlur * 3f + 2f;

        int safeOutputWidth = Math.max(1, outputWidth);
        int safeOutputHeight = Math.max(1, outputHeight);
        int width = clampInt(
                (int) Math.ceil(safeGlassWidth + padding * 2f),
                1,
                safeOutputWidth);
        int height = clampInt(
                (int) Math.ceil(safeGlassHeight + padding * 2f),
                1,
                safeOutputHeight);
        return new Spec(width, height, padding);
    }

    private static float finitePositive(float value) {
        return Float.isFinite(value) && value > 0f ? value : 0f;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private PrismalBloomTarget() {}
}
