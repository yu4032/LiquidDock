package com.hellovoid.prismal;

/** Skip work whose mathematical contribution is exactly zero in the flat glass interior. */
final class PrismalInteriorFastPath {
    private static final String GRADIENT =
            "vec2 gradHSig = computeGradientHeight(pPx, halfSz, crMask, u_sminSmoothing, tw);";
    private static final String REFLECTION = """
                vec3 reflSample;
                if (u_useBlurredTexture == 1) {
                    reflSample = texture2D(u_blurredTexture, reflUv).rgb;
                } else {
                    reflSample = texture2D(u_backgroundTexture, reflUv).rgb;
                }
            """;

    private PrismalInteriorFastPath() {}

    static String apply(String shader) {
        // The legacy smoothed rounded-box field is at most 4 px away from any one-pixel
        // finite-difference sample. Beyond tw + 4 all four heights are exactly 1.
        // The continuous field is approximate, so use its exact box branch rather than
        // assuming it is a distance field. Corner reach is at most 0.2893 * radius;
        // outside all corner patches, all four probes are flat beyond tw + sampleStep.
        shader = replaceOnce(shader, GRADIENT, """
                bool continuousFlat = false;
                if (u_continuousCorners > 0.5) {
                    float sampleStep = max(u_sminSmoothing, 1.0);
                    float maxRadius = clamp(max(max(u_cornerRadii.x, u_cornerRadii.y),
                            max(u_cornerRadii.z, u_cornerRadii.w)),
                            0.0, min(halfSz.x, halfSz.y));
                    vec2 cornerExtent = min(vec2(1.29 * maxRadius), halfSz);
                    vec2 boxDepth = halfSz - abs(pPx);
                    vec2 cornerProbe = abs(pPx) + vec2(sampleStep)
                            - (halfSz - cornerExtent);
                    continuousFlat = min(boxDepth.x, boxDepth.y) >= tw + sampleStep + 0.01
                            && min(cornerProbe.x, cornerProbe.y) <= 0.0;
                }
                vec2 gradHSig = vec2(0.0);
                if (!continuousFlat) {
                    if (u_continuousCorners > 0.5 || edgeDist < tw + 4.0) {
                        gradHSig = computeGradientHeight(pPx, halfSz, crMask, u_sminSmoothing, tw);
                    }
                }
                """);
        return replaceOnce(shader, REFLECTION, """
                vec3 reflSample = vec3(0.0);
                if (reflW > 0.0) {
                    if (u_useBlurredTexture == 1) {
                        reflSample = texture2D(u_blurredTexture, reflUv).rgb;
                    } else {
                        reflSample = texture2D(u_backgroundTexture, reflUv).rgb;
                    }
                }
                """);
    }

    private static String replaceOnce(String source, String expected, String replacement) {
        if (source == null) throw new IllegalArgumentException("shader == null");
        int start = source.indexOf(expected);
        if (start < 0 || source.indexOf(expected, start + expected.length()) >= 0)
            throw new IllegalArgumentException("Prismal interior shader contract changed");
        return source.substring(0, start) + replacement + source.substring(start + expected.length());
    }
}
