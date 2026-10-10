package com.hellovoid.prismal;

/**
 * LiquidDock's single-edge lens keeps the upstream Prismal shader untouched.
 * Since that edge path replaces upstream Snell transmission, the refractive index
 * must also modulate the surviving lens displacement, not just Fresnel reflectance.
 */
public final class PrismalIorLensPolicy {
    /** Existing default profile; its rendered lens strength must stay unchanged. */
    private static final float REFERENCE_IOR = 1.55f;
    private static final float REFERENCE_BEND = 1f - 1f / REFERENCE_IOR;

    private PrismalIorLensPolicy() {}

    public static float relativeBend(float ior) {
        if (!Float.isFinite(ior)) return 1f;
        // At n=1 light does not refract. Normalization preserves every existing
        // default lens profile at n=1.55; n=2 reaches ~1.41x the default bend.
        float n = Math.max(1f, ior);
        float ratio = (1f - 1f / n) / REFERENCE_BEND;
        return Math.max(0f, Math.min(1.5f, ratio));
    }
}
