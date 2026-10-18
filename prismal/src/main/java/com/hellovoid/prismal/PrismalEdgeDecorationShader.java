package com.hellovoid.prismal;

/**
 * Pure insertion around upstream Prismal's existing rounded-rect SDF.
 * No changes to lens, OS4 Fresnel/light/reflection or the vendored shader.
 */
final class PrismalEdgeDecorationShader {
    private static final String DIST =
            "float distMask = sdRoundBox(pPx, halfSz, crMask, u_sminSmoothing);";
    private static final String FINAL =
            "gl_FragColor = vec4(color, opacity * u_transmittance);";

    private PrismalEdgeDecorationShader() {}

    static String apply(String shader) {
        if (shader == null || shader.indexOf(DIST) < 0 || shader.indexOf(FINAL) < 0
                || shader.indexOf(DIST) != shader.lastIndexOf(DIST)
                || shader.indexOf(FINAL) != shader.lastIndexOf(FINAL)) {
            throw new IllegalStateException("Prismal edge decoration shader anchor changed");
        }
        return shader.replace("precision highp float;", """
                precision highp float;
                uniform vec4 u_outerStrokeColor;
                uniform float u_outerStrokeWidthPx;
                uniform float u_strokeLightResponse;
                uniform vec4 u_dropShadowColor;
                uniform float u_dropShadowRadiusPx;
                uniform float u_dropShadowOffsetYPx;
                uniform float u_dropShadowLightFollow;
                """)
                .replace(DIST, DIST + """
                    // Exterior pixels use the original Prismal SDF only; no extra
                    // sdRoundBox, gradSDF, derivative, exp, or pow operations.
                    // This path runs before the backdrop/refraction sampling.
                    if (distMask > 0.0) {
                        vec2 edgeN = normalize(pPx / max(halfSz, vec2(1.0))
                                + vec2(0.0001));
                        vec2 light = normalize(u_lightDir + vec2(0.0001));
                        float facing = dot(edgeN, light);
                        float rim = 0.0;
                        if (u_outerStrokeColor.a > 0.0 && u_outerStrokeWidthPx > 0.0) {
                            float lit = mix(1.0, 0.8 + 0.4 * max(facing, 0.0),
                                    u_strokeLightResponse);
                            rim = u_outerStrokeColor.a * lit * (1.0 - smoothstep(
                                    0.0, u_outerStrokeWidthPx, distMask));
                        }
                        float shade = 0.0;
                        if (u_dropShadowColor.a > 0.0 && u_dropShadowRadiusPx > 0.0) {
                            // Project the cast offset onto the SDF normal.
                            // Existing optical light direction drives the shadow too.
                            float offset = -facing * u_dropShadowRadiusPx * 0.42
                                    * u_dropShadowLightFollow
                                    - edgeN.y * u_dropShadowOffsetYPx;
                            float reach = max(0.0, distMask - offset);
                            float falloff = 1.0 - smoothstep(
                                    0.0, u_dropShadowRadiusPx * 2.0, reach);
                            shade = u_dropShadowColor.a * falloff * falloff;
                        }
                        float edgeAlpha = clamp(rim + shade * (1.0 - rim), 0.0, 1.0);
                        if (edgeAlpha < 0.002) discard;
                        vec3 edgeRgb = (u_outerStrokeColor.rgb * rim
                                + u_dropShadowColor.rgb * shade * (1.0 - rim))
                                / max(edgeAlpha, 0.002);
                        gl_FragColor = vec4(edgeRgb, edgeAlpha * u_transmittance);
                        return;
                    }
                """)
                .replace(FINAL, """
                    // Prismal already computed 'outward' from its optical SDF.
                    // Reuse that normal for the inner half of the custom stroke.
                    if (u_outerStrokeColor.a > 0.0 && u_outerStrokeWidthPx > 0.0) {
                        vec2 light = normalize(u_lightDir + vec2(0.0001));
                        float facing = max(dot(vec2(outward.x, -outward.y), light), 0.0);
                        float lit = mix(1.0, 0.8 + 0.4 * facing,
                                u_strokeLightResponse);
                        float insideStroke = 1.0 - smoothstep(
                                0.0, u_outerStrokeWidthPx, -distMask);
                        color = mix(color, u_outerStrokeColor.rgb,
                                clamp(u_outerStrokeColor.a * lit * insideStroke, 0.0, 1.0));
                    }
                    """ + FINAL);
    }
}
