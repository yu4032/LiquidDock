package com.hellovoid.prismal;

/** Immutable stroke and cast-shadow recipe. Lengths are dp until rasterization. */
public final class PrismalEdgeDecoration {
    public static final PrismalEdgeDecoration NONE =
            new PrismalEdgeDecoration(0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);

    public final float strokeWidthDp, strokeR, strokeG, strokeB, strokeA;
    public final float shadowRadiusDp, shadowOffsetYDp, shadowR, shadowG, shadowB, shadowA;
    public final float shadowLightFollow, strokeLightResponse;

    public PrismalEdgeDecoration(float strokeWidthDp,
                                 float strokeR, float strokeG, float strokeB, float strokeA,
                                 float shadowRadiusDp, float shadowOffsetYDp,
                                 float shadowR, float shadowG, float shadowB, float shadowA,
                                 float shadowLightFollow, float strokeLightResponse) {
        this.strokeWidthDp = safe(strokeWidthDp, 0f, 4f);
        this.strokeR = safe(strokeR, 0f, 1f);
        this.strokeG = safe(strokeG, 0f, 1f);
        this.strokeB = safe(strokeB, 0f, 1f);
        this.strokeA = safe(strokeA, 0f, 1f);
        this.shadowRadiusDp = safe(shadowRadiusDp, 0f, 24f);
        this.shadowOffsetYDp = safe(shadowOffsetYDp, -16f, 16f);
        this.shadowR = safe(shadowR, 0f, 1f);
        this.shadowG = safe(shadowG, 0f, 1f);
        this.shadowB = safe(shadowB, 0f, 1f);
        this.shadowA = safe(shadowA, 0f, 1f);
        this.shadowLightFollow = safe(shadowLightFollow, 0f, 1f);
        this.strokeLightResponse = safe(strokeLightResponse, 0f, 1f);
    }

    public boolean hasStroke() { return strokeA > 0f && strokeWidthDp > 0f; }
    public boolean hasShadow() { return shadowA > 0f && shadowRadiusDp > 0f; }

    /** Native Dock/SystemUI shadows own external pixels, but Prismal may own the stroke. */
    public PrismalEdgeDecoration strokeOnly() {
        if (!hasShadow()) return this;
        return new PrismalEdgeDecoration(strokeWidthDp, strokeR, strokeG, strokeB, strokeA,
                0f, 0f, shadowR, shadowG, shadowB, 0f,
                0f, strokeLightResponse);
    }

    /** Extra guard beyond the upstream Prismal AA raster fringe. */
    public float extraGuardPx(float density) {
        float scale = safe(density, .1f, 5f);
        float stroke = hasStroke() ? strokeWidthDp * scale : 0f;
        float shadow = hasShadow()
                ? (shadowRadiusDp * (2.5f + 0.45f * shadowLightFollow)
                        + Math.abs(shadowOffsetYDp)) * scale : 0f;
        return Math.min(96f, Math.max(stroke, shadow));
    }

    private static float safe(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
