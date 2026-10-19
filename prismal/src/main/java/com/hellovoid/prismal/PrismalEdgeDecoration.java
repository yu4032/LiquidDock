package com.hellovoid.prismal;

/**
 * Immutable per-component optical response, in the original glass draw.
 * The narrow contour derives RGB from refracted backdrop pixels and the same
 * Prismal OS4 highlights. Lighting varies edge color instead of fading out
 * a fixed-black painted stroke.
 */
public final class PrismalEdgeDecoration {
    public static final PrismalEdgeDecoration NONE =
            new PrismalEdgeDecoration(0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f);

    public final float backlitWidthDp, backlitStrength, backlitSoftness, backlitExtent;
    /** Subpixel optical rim fitting on the native Dock body, independent of shadow overscan. */
    public final float inkViewportInsetPx;
    public final float shadowRadiusDp, shadowOffsetYDp;
    public final float shadowR, shadowG, shadowB, shadowA, shadowLightFollow;

    public PrismalEdgeDecoration(float backlitWidthDp, float backlitStrength,
                                 float backlitSoftness, float shadowRadiusDp,
                                 float shadowOffsetYDp, float shadowR,
                                 float shadowG, float shadowB, float shadowA,
                                 float shadowLightFollow) {
        this(backlitWidthDp, backlitStrength, backlitSoftness, shadowRadiusDp,
                shadowOffsetYDp, shadowR, shadowG, shadowB, shadowA,
                shadowLightFollow, 0.55f);
    }

    public PrismalEdgeDecoration(float backlitWidthDp, float backlitStrength,
                                 float backlitSoftness, float shadowRadiusDp,
                                 float shadowOffsetYDp, float shadowR,
                                 float shadowG, float shadowB, float shadowA,
                                 float shadowLightFollow, float backlitExtent) {
        this(backlitWidthDp, backlitStrength, backlitSoftness, shadowRadiusDp,
                shadowOffsetYDp, shadowR, shadowG, shadowB, shadowA,
                shadowLightFollow, backlitExtent, 0f);
    }

    private PrismalEdgeDecoration(float backlitWidthDp, float backlitStrength,
                                  float backlitSoftness, float shadowRadiusDp,
                                  float shadowOffsetYDp, float shadowR, float shadowG,
                                  float shadowB, float shadowA, float shadowLightFollow,
                                  float backlitExtent, float inkViewportInsetPx) {
        this.inkViewportInsetPx = bounded(inkViewportInsetPx, 0f, 2f);
        this.backlitWidthDp = bounded(backlitWidthDp, 0f, 24f);
        this.backlitStrength = bounded(backlitStrength, 0f, 1f);
        this.backlitSoftness = bounded(backlitSoftness, 0.05f, 2f);
        this.backlitExtent = bounded(backlitExtent, 0f, 1f);
        this.shadowRadiusDp = bounded(shadowRadiusDp, 0f, 24f);
        this.shadowOffsetYDp = bounded(shadowOffsetYDp, -16f, 16f);
        this.shadowR = bounded(shadowR, 0f, 1f);
        this.shadowG = bounded(shadowG, 0f, 1f);
        this.shadowB = bounded(shadowB, 0f, 1f);
        this.shadowA = bounded(shadowA, 0f, 1f);
        this.shadowLightFollow = bounded(shadowLightFollow, 0f, 1f);
    }

    /**
     * Orientation domain: top/bottom surfaces reflect; left/right surfaces
     * retain the refracted hue but attenuate it. Extent controls the starting
     * axis ratio, softness controls the span across rounded corners.
     * Preserved preference values .55/.60 map to ~28°..68°.
     */
    public float backlitSideStart() {
        if (backlitExtent <= 0f) return 2f; // shadow-only shader sentinel
        return bounded(0.65f - 0.55f * backlitExtent, 0.10f, 0.65f);
    }

    public float backlitSideSpan() {
        return bounded(0.19f + 0.60f * Math.min(1f, backlitSoftness * 0.5f),
                0.12f, 0.78f);
    }

    public boolean hasBacklit() {
        return backlitExtent > 0f && backlitWidthDp > 0f && backlitStrength > 0f;
    }
    public boolean hasShadow() { return shadowRadiusDp > 0f && shadowA > 0f; }

    /**
     * Dock only: keep its native foreground and glass geometry pixel-exact.
     * Fit just the antialiased optical ink independently of exterior shadow.
     * Cached on preference changes, never allocated on a render frame.
     */
    public PrismalEdgeDecoration fitInkToNativeViewport(float insetPx) {
        if (!hasBacklit() || insetPx <= 0f) return this;
        return new PrismalEdgeDecoration(backlitWidthDp, backlitStrength,
                backlitSoftness, shadowRadiusDp, shadowOffsetYDp, shadowR, shadowG,
                shadowB, shadowA, shadowLightFollow, backlitExtent, insetPx);
    }

    public float effectiveShadowRadiusPx(float density, float minDimensionPx) {
        if (!hasShadow()) return 0f;
        float scale = bounded(density, .1f, 5f);
        return Math.min(shadowRadiusDp * scale,
                Math.max(1f, minDimensionPx * 0.18f));
    }

    public float effectiveShadowOffsetYPx(float density, float minDimensionPx) {
        float radius = effectiveShadowRadiusPx(density, minDimensionPx);
        return radius <= 0f ? 0f : bounded(shadowOffsetYDp * bounded(density, .1f, 5f),
                -radius * 0.6f, radius * 0.6f);
    }

    public float extraGuardPx(float density, float minDimensionPx) {
        float scale = bounded(density, .1f, 5f);
        // Unlike the previous inner line, an exterior hairline needs actual
        // extra vertex coverage AND an extended node scissor. Shared guard
        // also expands the Dock dirty region; no extra render pass is needed.
        float inkGuard = hasBacklit() ? Math.min(32f,
                backlitWidthDp * scale + 2f) : 0f;
        float radius = effectiveShadowRadiusPx(density, minDimensionPx);
        if (radius <= 0f) return inkGuard;
        float shadowGuard = Math.min(96f,
                radius * (2.5f + 0.42f * shadowLightFollow)
                + Math.abs(effectiveShadowOffsetYPx(density, minDimensionPx)));
        return Math.max(inkGuard, shadowGuard);
    }

    private static float bounded(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
