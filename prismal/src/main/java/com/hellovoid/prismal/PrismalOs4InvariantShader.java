package com.hellovoid.prismal;

/**
 * Hoists configuration-only OS4/Prismal values out of the fragment shader.
 *
 * <p>These expressions depend only on immutable PrismalParams, not geometry or pixels. The
 * renderer uploads numerically equivalent uniforms once when params change.</p>
 */
final class PrismalOs4InvariantShader {
    private static final String DECLARATION_ANCHOR = "uniform vec2  u_lightDir;";

    private PrismalOs4InvariantShader() {}

    static String apply(String fragment) {
        if (fragment == null) throw new IllegalArgumentException("fragment == null");
        String corrected = replaceExactlyOnce(
                fragment,
                DECLARATION_ANCHOR,
                DECLARATION_ANCHOR + "\n"
                        + "uniform vec3 u_invariantLightPrimary;\n"
                        + "uniform vec2 u_invariantLightXY;\n"
                        + "uniform vec3 u_invariantOs4LightDir;\n"
                        + "uniform float u_invariantFresnelR0;\n"
                        + "uniform float u_invariantShadowExtent;",
                "invariant uniform declaration");
        corrected = replaceExactlyOnce(
                corrected,
                "vec3 Lp = normalize(vec3(u_lightDir, 1.45));",
                "vec3 Lp = u_invariantLightPrimary;",
                "primary light");
        corrected = replaceExactlyOnce(
                corrected,
                "vec2 Lxy = normalize(u_lightDir + vec2(1e-5));",
                "vec2 Lxy = u_invariantLightXY;",
                "2D light");
        corrected = replaceExactlyOnce(
                corrected,
                "vec3 os4LightDir3 = normalize(vec3(Lxy, 1.0));",
                "vec3 os4LightDir3 = u_invariantOs4LightDir;",
                "OS4 light");
        corrected = replaceExactlyOnce(
                corrected,
                "float r0 = pow((1.0 - u_ior) / (1.0 + u_ior), 2.0);",
                "float r0 = u_invariantFresnelR0;",
                "Fresnel R0");
        return replaceExactlyOnce(
                corrected,
                "float shadowExt = mix(0.15, 0.60, clamp(u_shadowSoftness / 20.0, 0.0, 1.0));",
                "float shadowExt = u_invariantShadowExtent;",
                "shadow extent");
    }

    /** Packed: primary.xyz, lightXY.xy, os4.xyz, fresnelR0, shadowExtent. */
    static void compute(PrismalParams p, float[] out) {
        if (p == null) throw new IllegalArgumentException("params == null");
        if (out == null || out.length < 10) throw new IllegalArgumentException("need 10 floats");
        normalize3(p.lightDirX, p.lightDirY, 1.45f, out, 0);

        float lx = p.lightDirX + 1e-5f;
        float ly = p.lightDirY + 1e-5f;
        float l2 = (float) Math.sqrt(lx * lx + ly * ly);
        if (l2 <= 0f) {
            out[3] = 0f;
            out[4] = 0f;
        } else {
            out[3] = lx / l2;
            out[4] = ly / l2;
        }
        normalize3(out[3], out[4], 1f, out, 5);

        float denominator = 1f + p.ior;
        float ratio = denominator != 0f ? (1f - p.ior) / denominator : 0f;
        out[8] = ratio * ratio;

        float shadowT = clamp(p.shadowSoftness / 20f, 0f, 1f);
        out[9] = 0.15f + (0.60f - 0.15f) * shadowT;
    }

    private static void normalize3(float x, float y, float z, float[] out, int offset) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        out[offset] = x / len;
        out[offset + 1] = y / len;
        out[offset + 2] = z / len;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String replaceExactlyOnce(
            String source, String oldText, String newText, String label) {
        int first = source.indexOf(oldText);
        if (first < 0 || source.indexOf(oldText, first + oldText.length()) >= 0) {
            throw new IllegalStateException("Prismal " + label + " contract changed");
        }
        return source.substring(0, first) + newText + source.substring(first + oldText.length());
    }
}
