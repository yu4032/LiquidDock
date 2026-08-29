package com.hellovoid.prismal;

/** Scales Prismal's physical optical edge bands without drawing a separate outline. */
public final class PrismalOpticalEdgeShader {
    private static final String PRECISION = "precision highp float;";
    private static final String EDGE_DISTANCE = "float edgeDist = -distMask;";
    private static final String OPACITY =
            "float opacity = 1.0 - smoothstep(-inset * 0.5, 0.0, distMask);";

    public static String apply(String source) {
        requireSingle(source, PRECISION);
        requireSingle(source, EDGE_DISTANCE);
        requireSingle(source, OPACITY);
        String patched = source
                .replace(PRECISION,
                        "#extension GL_OES_standard_derivatives : enable\n\n" + PRECISION)
                .replace(EDGE_DISTANCE, EDGE_DISTANCE + "\n"
                        + "    float opticalEdgeScale = clamp(u_highlightWidth, 0.5, 3.0);\n"
                        + "    float edgeAa = max(fwidth(distMask), 0.75);")
                .replace(OPACITY,
                        "float opacity = 1.0 - smoothstep(-edgeAa, edgeAa, distMask);")
                .replace("minDim * 0.09", "minDim * 0.09 * opticalEdgeScale")
                .replace("tw * 0.42", "tw * 0.42 * opticalEdgeScale")
                .replace("minDim * 0.12", "minDim * 0.12 * opticalEdgeScale")
                .replace("bandFracR * rimBandTight",
                        "bandFracR * opticalEdgeScale * rimBandTight");

        // GLSL ES defines smoothstep only for edge0 < edge1. Upstream Prismal uses these
        // calls as descending ramps, including the meniscus normal feeding the near-white
        // specular term. Preserve the intended ramp exactly while making its evaluation defined
        // on every GPU instead of relying on driver-specific edge reversal behavior.
        patched = replaceDescendingExactlyOnce(
                patched, "128.0", "46.0", "minDim * 2.0");
        patched = replaceDescendingExactlyOnce(
                patched,
                "clamp(minDim * 0.09 * opticalEdgeScale, 1.8, 18.0)",
                "0.0", "edgeDist");
        patched = replaceDescendingExactlyOnce(
                patched, "tw * 0.42 * opticalEdgeScale", "0.0", "edgeDist");
        patched = replaceDescendingExactlyOnce(
                patched, "refractionHeight", "0.0", "edgeDist");
        patched = replaceDescendingExactlyOnce(
                patched, "silW", "0.0", "edgeDist");
        patched = replaceDescendingExactlyOnce(
                patched, "chromaFar", "0.0", "edgeDist");
        patched = replaceDescendingExactlyOnce(
                patched, "bandR", "bandR * 0.06", "edgeDist");
        patched = replaceDescendingExactlyOnce(
                patched, "bandR * 1.8", "bandR * 0.08", "edgeDist");
        patched = replaceDescendingExactlyOnce(
                patched, "bandR * 0.95", "bandR * 0.05", "edgeDist");
        patched = replaceDescendingExactlyOnce(
                patched, "glowR", "glowR * 0.5", "length(pPx - glowPx)");
        return patched;
    }

    private static String replaceDescendingExactlyOnce(
            String source, String high, String low, String value) {
        String original = "smoothstep(" + high + ", " + low + ", " + value + ")";
        requireSingle(source, original);
        String defined = "(1.0 - smoothstep(" + low + ", " + high + ", " + value + "))";
        return source.replace(original, defined);
    }

    private static void requireSingle(String source, String anchor) {
        int first = source.indexOf(anchor);
        if (first < 0 || source.indexOf(anchor, first + anchor.length()) >= 0) {
            throw new IllegalArgumentException(
                    "Expected exactly one Prismal optical-edge anchor: " + anchor);
        }
    }

    private PrismalOpticalEdgeShader() {}
}
