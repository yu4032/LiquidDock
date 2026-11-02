package com.hellovoid.liquiddock;

/**
 * Reduce sampling detail within one optical model; never interpolate different refraction fields.
 */
final class LauncherGlassHwuiTransitionShader {
    static String apply(String source, boolean decorated) {
        source =
                replaceOnce(
                        source,
                        "uniform vec4 uProbeBounds;",
                        "uniform vec4 uProbeBounds;\nuniform float u_workspaceDetail;");
        source =
                replaceOnce(
                        source,
                        "u_causticIntensity > 0.001",
                        "u_causticIntensity * u_workspaceDetail > 0.001");
        source =
                replaceOnce(
                        source,
                        "* u_causticIntensity * height",
                        "* (u_causticIntensity * u_workspaceDetail) * height");
        // The exact interior fast path may also run while transition caustics contribute zero.
        source =
                source.replace(
                        "u_causticIntensity <= 0.001",
                        "u_causticIntensity * u_workspaceDetail <= 0.001");
        if (!decorated || !source.contains("vec3 optical27SevenTap(")) return source;

        String signature =
                "vec3 optical27SevenTap(vec2 offset, vec2 push, float pinch, vec2 screenUv) {";
        source =
                replaceOnce(
                        source,
                        signature,
                        "vec3 workspaceCompleteSampling(vec2 offset, vec2 push, float pinch, vec2"
                            + " screenUv) {");
        return replaceOnce(
                source,
                "half4 main(float2 fragCoord) {",
                """

                vec3 optical27SevenTap(vec2 offset, vec2 push, float pinch, vec2 screenUv) {
                    float q = clamp(u_workspaceDetail, 0.0, 1.0);
                    if (q >= 1.0) return workspaceCompleteSampling(offset, push, pinch, screenUv);
                    // Collapse three of the seven taps to the channel centroids. Adjust their
                    // positions as weights expand so every channel keeps its first moment.
                    float outer = (7.0 / 9.0 - q * (5.0 / 18.0)) / (1.0 - q * 0.5);
                    float green = (1.0 - q) * (4.0 / 27.0) / (1.0 - q * (2.0 / 3.0));
                    vec3 r = sampleBlurred(backdropUv(screenUv,
                            offset + push * outer * u_dispersionR, pinch)).rgb;
                    vec3 g = sampleBlurred(backdropUv(screenUv,
                            offset + push * green * (u_dispersionR - u_dispersionB), pinch)).rgb;
                    vec3 b = sampleBlurred(backdropUv(screenUv,
                            offset - push * outer * u_dispersionB, pinch)).rgb;
                    if (q <= 0.0) return vec3(r.r, g.g, b.b);
                    vec3 accum = vec3(r.r * (1.0 - q * 0.5),
                            g.g * (1.0 - q * (2.0 / 3.0)), b.b * (1.0 - q * 0.5));
                    for (int i = 1; i < 3; ++i) {
                        float w = 1.0 - float(i) / 3.0;
                        vec3 c = sampleBlurred(backdropUv(screenUv,
                                offset + push * w * u_dispersionR, pinch)).rgb;
                        accum.r += c.r * q * w * 0.5;
                        accum.g += c.g * q * (1.0 - w) / 3.0;
                    }
                    for (int j = 1; j < 3; ++j) {
                        float w = float(j) / 3.0;
                        vec3 c = sampleBlurred(backdropUv(screenUv,
                                offset - push * w * u_dispersionB, pinch)).rgb;
                        accum.g += c.g * q * (1.0 - w) / 3.0;
                        accum.b += c.b * q * w * 0.5;
                    }
                    return accum;
                }
                """
                        + "half4 main(float2 fragCoord) {");
    }

    private static String replaceOnce(String source, String token, String replacement) {
        int start = source.indexOf(token);
        if (start < 0 || source.indexOf(token, start + token.length()) >= 0) {
            throw new IllegalArgumentException(
                    "Workspace quality shader contract changed: " + token);
        }
        return source.substring(0, start) + replacement + source.substring(start + token.length());
    }
}
