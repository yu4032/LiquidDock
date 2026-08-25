package com.hellovoid.prismal;

/** Injects the configurable SDF edge stroke shared by every Prismal glass surface. */
public final class PrismalEdgeAntialiasShader {
    private static final String UNIFORM_ANCHOR = "uniform float u_rimStrength;";
    private static final String OUTPUT_ANCHOR = "gl_FragColor = vec4(color, opacity * u_transmittance);";
    private static final String UNIFORMS = """

uniform int u_glassStrokeEnabled;
uniform int u_glassStrokeFillDiff;
uniform float u_glassStrokeFillDiffWidth;
uniform float u_glassStrokeStandardWidth;
uniform vec4 u_glassStrokeColor;
            """;
    private static final String COMPOSITE = """
    if (u_glassStrokeEnabled == 1 && u_glassStrokeColor.a > 0.0) {
        float strokeAa = 0.75;
        float fillWidth = max(u_glassStrokeFillDiffWidth, 0.0);
        float outerCoverage = smoothstep(strokeAa, -strokeAa, distMask);
        float innerCoverage = smoothstep(fillWidth - strokeAa, fillWidth + strokeAa, edgeDist);
        float fillDiffMask = outerCoverage - innerCoverage;
        float halfStandardWidth = max(u_glassStrokeStandardWidth * 0.5, 0.0);
        float standardMask = 1.0 - smoothstep(
                max(halfStandardWidth - strokeAa, 0.0),
                halfStandardWidth + strokeAa, abs(distMask));
        float glassStrokeMask = clamp(
                u_glassStrokeFillDiff == 1 ? fillDiffMask : standardMask, 0.0, 1.0);
        color = mix(color, u_glassStrokeColor.rgb,
                glassStrokeMask * clamp(u_glassStrokeColor.a, 0.0, 1.0));
    }
    """;

    public static String apply(String source) {
        requireSingle(source, UNIFORM_ANCHOR);
        requireSingle(source, OUTPUT_ANCHOR);
        return source.replace(UNIFORM_ANCHOR, UNIFORM_ANCHOR + UNIFORMS)
                .replace(OUTPUT_ANCHOR, COMPOSITE + OUTPUT_ANCHOR);
    }

    private static void requireSingle(String source, String anchor) {
        int first = source.indexOf(anchor);
        if (first < 0 || source.indexOf(anchor, first + anchor.length()) >= 0) {
            throw new IllegalArgumentException("Expected exactly one Prismal shader anchor: " + anchor);
        }
    }

    private PrismalEdgeAntialiasShader() {}
}
