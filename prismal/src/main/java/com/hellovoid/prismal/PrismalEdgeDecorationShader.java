package com.hellovoid.prismal;

/**
 * One-pass optional exterior shadow / contour stroke, sharing the upstream
 * Prismal SDF, OS4 SDF normal and precomputed light direction.
 *
 * No additional draw, backdrop, FBO, SDF evaluation, or light normalization.
 * Constant arithmetic per covered fragment; raster/scissor expansion bounds
 * the actual number of affected pixels.
 */
final class PrismalEdgeDecorationShader {
    private static final String PRECISION = "precision highp float;";
    private static final String MAIN = "void main()";
    private static final String BEFORE_DISCARD = "if (opacity < 0.001) discard;";
    private static final String FINAL =
            "gl_FragColor = vec4(color, opacity * u_transmittance);";

    private PrismalEdgeDecorationShader() {}

    static String apply(String shader) {
        requireOnce(shader, PRECISION);
        requireOnce(shader, MAIN);
        requireOnce(shader, BEFORE_DISCARD);
        requireOnce(shader, FINAL);
        if (!shader.contains("vec2 os4SdfGradient = vec2(")
                || !shader.contains("uniform vec2 u_invariantLightXY;")
                || !shader.contains("uniform float u_os4DirectionalIntensity;")
                || !shader.contains("uniform float u_os4DirectionalOppositeIntensity;")) {
            throw new IllegalStateException("Prismal OS4 edge/light invariants changed");
        }

        return shader.replace(PRECISION, PRECISION + """
                uniform vec4 u_outerStrokeColor;
                uniform float u_outerStrokeWidthPx;
                uniform float u_strokeLightResponse;
                uniform float u_strokeMinCos;
                uniform vec4 u_dropShadowColor;
                uniform float u_dropShadowRadiusPx;
                uniform float u_dropShadowOffsetYPx;
                uniform float u_dropShadowLightFollow;
                """)
                .replace(MAIN, """
                // Same main/opposite directional strengths as OS4 glass lighting.
                // A balanced illumination field has no directional dark contour.
                // No world-space side assumption: the result follows light rotation.
                vec2 prismalBacklitContour(float normalDotLight) {
                    float contrast = max(u_os4DirectionalIntensity, 0.0)
                            - max(u_os4DirectionalOppositeIntensity, 0.0);
                    float backlitNormal = -normalDotLight * sign(contrast);
                    float aperture = clamp(
                            (backlitNormal - u_strokeMinCos)
                            / max(1.0 - u_strokeMinCos, 0.001), 0.0, 1.0);
                    float taper = aperture * aperture * (3.0 - 2.0 * aperture);
                    float energy = clamp(abs(contrast), 0.0, 1.0)
                            * clamp(u_strokeLightResponse, 0.0, 1.0)
                            * step(0.5, u_os4EdgeEnabled);
                    return vec2(taper, energy);
                }
                """ + MAIN)
                .replace(BEFORE_DISCARD, """
                    // Run after upstream OS4 fwidth/dFdx/dFdy so helper quads can
                    // compute derivatives before any optional exterior early return.
                    // Exterior pixels would otherwise be discarded by original glass.
                    if (distMask > 0.0) {
                        vec2 edgeN = normalize(os4SdfGradient + vec2(0.00001));
                        float facing = dot(edgeN, u_invariantLightXY);
                        vec2 backlight = prismalBacklitContour(facing);
                        float strokeWidth = u_outerStrokeWidthPx * backlight.x;
                        float rim = 0.0;
                        if (u_outerStrokeWidthPx > 0.0 && u_outerStrokeColor.a > 0.0) {
                            rim = u_outerStrokeColor.a * backlight.x * backlight.y
                                    * (1.0 - smoothstep(
                                            0.0, max(strokeWidth, 0.001), distMask));
                        }
                        float shade = 0.0;
                        if (u_dropShadowRadiusPx > 0.0 && u_dropShadowColor.a > 0.0) {
                            // Signed projection of the constant light vector onto
                            // the existing edge normal. No second sdRoundBox().
                            float projectedOffset = -facing * u_dropShadowRadiusPx * 0.42
                                    * u_dropShadowLightFollow
                                    - edgeN.y * u_dropShadowOffsetYPx;
                            float travel = max(0.0, distMask - projectedOffset);
                            float decay = 1.0 - smoothstep(
                                    0.0, u_dropShadowRadiusPx * 2.0, travel);
                            shade = u_dropShadowColor.a * decay * decay;
                        }
                        float alpha = clamp(rim + shade * (1.0 - rim), 0.0, 1.0);
                        if (alpha < 0.002) discard;
                        vec3 rgb = (u_outerStrokeColor.rgb * rim
                                + u_dropShadowColor.rgb * shade * (1.0 - rim))
                                / max(alpha, 0.002);
                        gl_FragColor = vec4(rgb, alpha * u_transmittance);
                        return;
                    }
                    """ + BEFORE_DISCARD)
                .replace(FINAL, """
                    // Interior half of the outline uses the same normal and
                    // pre-normalized direction as the OS4 rim shader.
                    if (u_outerStrokeWidthPx > 0.0 && u_outerStrokeColor.a > 0.0) {
                        vec2 edgeN = normalize(os4SdfGradient + vec2(0.00001));
                        float facing = dot(edgeN, u_invariantLightXY);
                        vec2 backlight = prismalBacklitContour(facing);
                        float strokeWidth = u_outerStrokeWidthPx * backlight.x;
                        float rim = 1.0 - smoothstep(
                                0.0, max(strokeWidth, 0.001), -distMask);
                        color = mix(color, u_outerStrokeColor.rgb,
                                clamp(u_outerStrokeColor.a * backlight.x * backlight.y * rim, 0.0, 1.0));
                    }
                    """ + FINAL);
    }

    private static void requireOnce(String shader, String needle) {
        if (shader == null) throw new IllegalArgumentException("shader == null");
        int first = shader.indexOf(needle);
        if (first < 0 || shader.indexOf(needle, first + needle.length()) >= 0) {
            throw new IllegalStateException("Optional edge shader anchor changed: " + needle);
        }
    }
}
