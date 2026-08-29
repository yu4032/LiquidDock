package com.hellovoid.prismal;

/** Scales Prismal's physical optical edge bands without drawing a separate outline. */
public final class PrismalOpticalEdgeShader {
    private static final String PRECISION = "precision highp float;";
    private static final String MAIN = "void main() {";
    private static final String EDGE_DISTANCE = "float edgeDist = -distMask;";
    private static final String OPACITY =
            "float opacity = 1.0 - smoothstep(-inset * 0.5, 0.0, distMask);";
    private static final String SHELL_RIM =
            "smoothstep(bandR, bandR * 0.06, edgeDist)";
    private static final String FACE_SHEEN =
            "smoothstep(bandR * 1.8, bandR * 0.08, edgeDist)";
    private static final String PLAIN_HIGHLIGHT =
            "smoothstep(bandR * 0.95, bandR * 0.05, edgeDist)";
    private static final String AA_BAND_HELPER = """
            float aaDescendingBand(float outer, float inner, float value, float aa) {
                float low = min(inner, outer);
                float high = max(inner, outer);
                float center = (low + high) * 0.5;
                float halfWidth = max((high - low) * 0.5, max(aa, 0.001) * 0.5);
                return 1.0 - smoothstep(center - halfWidth, center + halfWidth, value);
            }
            """;

    public static String apply(String source) {
        requireSingle(source, PRECISION);
        requireSingle(source, MAIN);
        requireSingle(source, EDGE_DISTANCE);
        requireSingle(source, OPACITY);
        requireSingle(source, SHELL_RIM);
        requireSingle(source, FACE_SHEEN);
        requireSingle(source, PLAIN_HIGHLIGHT);
        return source
                .replace(PRECISION,
                        "#extension GL_OES_standard_derivatives : enable\n\n" + PRECISION)
                .replace(MAIN, AA_BAND_HELPER + "\n" + MAIN)
                .replace(EDGE_DISTANCE, EDGE_DISTANCE + "\n"
                        + "    float opticalEdgeScale = clamp(u_highlightWidth, 0.5, 3.0);\n"
                        + "    float edgeAa = max(fwidth(distMask), 0.75);")
                .replace(OPACITY,
                        "float opacity = 1.0 - smoothstep(-edgeAa, edgeAa, distMask);")
                .replace(SHELL_RIM,
                        "aaDescendingBand(bandR, bandR * 0.06, edgeDist, edgeAa)")
                .replace(FACE_SHEEN,
                        "aaDescendingBand(bandR * 1.8, bandR * 0.08, edgeDist, edgeAa)")
                .replace(PLAIN_HIGHLIGHT,
                        "aaDescendingBand(bandR * 0.95, bandR * 0.05, edgeDist, edgeAa)")
                .replace("minDim * 0.09", "minDim * 0.09 * opticalEdgeScale")
                .replace("tw * 0.42", "tw * 0.42 * opticalEdgeScale")
                .replace("minDim * 0.12", "minDim * 0.12 * opticalEdgeScale")
                .replace("bandFracR * rimBandTight",
                        "bandFracR * opticalEdgeScale * rimBandTight");
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
