package com.hellovoid.prismal;

/**
 * Immutable per-component optical response, in the original glass draw.
 * Light-facing reflection is supplied by Prismal OS4; only the underlit rim
 * is attenuated here. The cast shadow uses the same overall silhouette.
 */
public final class PrismalEdgeDecoration {
    public static final PrismalEdgeDecoration NONE =
            new PrismalEdgeDecoration(0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f);

    public final float backlitWidthDp, backlitStrength, backlitSoftness;
    public final float shadowRadiusDp, shadowOffsetYDp;
    public final float shadowR, shadowG, shadowB, shadowA, shadowLightFollow;

    public PrismalEdgeDecoration(float backlitWidthDp, float backlitStrength,
                                 float backlitSoftness, float shadowRadiusDp,
                                 float shadowOffsetYDp, float shadowR,
                                 float shadowG, float shadowB, float shadowA,
                                 float shadowLightFollow) {
        this.backlitWidthDp = bounded(backlitWidthDp, 0f, 24f);
        this.backlitStrength = bounded(backlitStrength, 0f, 1f);
        this.backlitSoftness = bounded(backlitSoftness, 0.05f, 1f);
        this.shadowRadiusDp = bounded(shadowRadiusDp, 0f, 24f);
        this.shadowOffsetYDp = bounded(shadowOffsetYDp, -16f, 16f);
        this.shadowR = bounded(shadowR, 0f, 1f);
        this.shadowG = bounded(shadowG, 0f, 1f);
        this.shadowB = bounded(shadowB, 0f, 1f);
        this.shadowA = bounded(shadowA, 0f, 1f);
        this.shadowLightFollow = bounded(shadowLightFollow, 0f, 1f);
    }

    public boolean hasBacklit() { return backlitWidthDp > 0f && backlitStrength > 0f; }
    public boolean hasShadow() { return shadowRadiusDp > 0f && shadowA > 0f; }

    /** Preserve a native shadow owner while applying glass-internal optics. */
    public PrismalEdgeDecoration withoutCastShadow() {
        if (!hasShadow()) return this;
        return new PrismalEdgeDecoration(backlitWidthDp, backlitStrength,
                backlitSoftness, 0f, 0f, shadowR, shadowG, shadowB, 0f, 0f);
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

    public float extraGuardPx(float density) {
        return extraGuardPx(density, Float.MAX_VALUE);
    }

    public float extraGuardPx(float density, float minDimensionPx) {
        float radius = effectiveShadowRadiusPx(density, minDimensionPx);
        return radius <= 0f ? 0f : Math.min(96f,
                radius * (2.5f + 0.42f * shadowLightFollow)
                + Math.abs(effectiveShadowOffsetYPx(density, minDimensionPx)));
    }

    private static float bounded(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
