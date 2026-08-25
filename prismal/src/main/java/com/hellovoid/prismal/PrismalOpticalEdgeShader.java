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
        return source
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
