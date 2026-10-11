package com.hellovoid.prismal;

/**
 * Hoists uniform-only light-vector normalization out of the large per-pixel Prismal shader.
 *
 * <p>Upstream's optical shader source is kept unchanged. For each immutable PrismalParams,
 * the renderer uploads the same primary/secondary light and half-vectors once. Seven large
 * widgets must not recompute four normalize/sqrt operations for every shaded pixel.</p>
 */
final class PrismalLightVectorShader {
    private static final String ANCHOR = "uniform vec2  u_lightDir;";
    private static final String LIGHT_BLOCK = """
            vec3 Lp = normalize(vec3(u_lightDir, 1.45));
            vec3 Ls = normalize(vec3(-u_lightDir.x * 0.62 + 0.41, -u_lightDir.y * 0.62 + 0.33, 0.74));
            vec3 Hp = normalize(Lp + V);
            vec3 Hs = normalize(Ls + V);
            """;

    private PrismalLightVectorShader() {}

    static String apply(String fragment) {
        if (fragment == null) throw new IllegalArgumentException("fragment == null");
        String amended = replaceOnce(fragment, ANCHOR, ANCHOR + "\n"
                + "uniform vec3 u_lightPrimary;\n"
                + "uniform vec3 u_lightSecondary;\n"
                + "uniform vec3 u_halfPrimary;\n"
                + "uniform vec3 u_halfSecondary;", "light declaration");
        return replaceOnce(amended, LIGHT_BLOCK.stripTrailing(),
                "vec3 Lp = u_lightPrimary;\n"
                        + "    vec3 Ls = u_lightSecondary;\n"
                        + "    vec3 Hp = u_halfPrimary;\n"
                        + "    vec3 Hs = u_halfSecondary;", "light expression");
    }

    /** Four packed vec3 uniforms, in declaration order. Reuses caller-owned array per upload. */
    static void compute(float lightX, float lightY, float[] out) {
        if (out == null || out.length < 12) throw new IllegalArgumentException("need 12 floats");
        normalize(lightX, lightY, 1.45f, out, 0);
        normalize(-lightX * 0.62f + 0.41f, -lightY * 0.62f + 0.33f, 0.74f, out, 3);
        normalize(out[0], out[1], out[2] + 1f, out, 6);
        normalize(out[3], out[4], out[5] + 1f, out, 9);
    }

    private static void normalize(float x, float y, float z, float[] out, int offset) {
        // GLSL normalize(vec3(...)) and Java sqrt use the same Euclidean definition.
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        out[offset] = x / len;
        out[offset + 1] = y / len;
        out[offset + 2] = z / len;
    }

    private static String replaceOnce(String source, String from, String to, String label) {
        int first = source.indexOf(from);
        if (first < 0 || source.indexOf(from, first + from.length()) >= 0) {
            throw new IllegalStateException("Prismal " + label + " upstream contract changed");
        }
        return source.substring(0, first) + to + source.substring(first + from.length());
    }
}
