package com.hellovoid.prismal;

/** Immutable, per-node edge paint. All lengths are dp until the renderer applies density. */
public final class PrismalEdgeDecoration {
    public static final PrismalEdgeDecoration NONE =
            new PrismalEdgeDecoration(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public final float strokeWidthDp, strokeR, strokeG, strokeB, strokeA;
    public final float shadowRadiusDp, shadowOffsetYDp, shadowR, shadowG, shadowB, shadowA;

    public PrismalEdgeDecoration(float strokeWidthDp,
                                 float strokeR, float strokeG, float strokeB, float strokeA,
                                 float shadowRadiusDp, float shadowOffsetYDp,
                                 float shadowR, float shadowG, float shadowB, float shadowA) {
        this.strokeWidthDp = Math.max(0f, strokeWidthDp);
        this.strokeR = clamp01(strokeR);
        this.strokeG = clamp01(strokeG);
        this.strokeB = clamp01(strokeB);
        this.strokeA = clamp01(strokeA);
        this.shadowRadiusDp = Math.max(0f, shadowRadiusDp);
        this.shadowOffsetYDp = shadowOffsetYDp;
        this.shadowR = clamp01(shadowR);
        this.shadowG = clamp01(shadowG);
        this.shadowB = clamp01(shadowB);
        this.shadowA = clamp01(shadowA);
    }

    public boolean hasStroke() { return strokeA > 0f && strokeWidthDp > 0f; }
    public boolean hasShadow() { return shadowA > 0f && shadowRadiusDp > 0f; }

    /** Extra geometry/scissor reach beyond Prismal's existing 2–3 px AA guard. */
    public float extraGuardPx(float density) {
        float scale = Math.max(0.1f, density);
        float stroke = hasStroke() ? strokeWidthDp * scale : 0f;
        float shadow = hasShadow()
                ? (shadowRadiusDp * 2.5f + Math.abs(shadowOffsetYDp)) * scale : 0f;
        return Math.min(64f, Math.max(stroke, shadow));
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
    }
}
