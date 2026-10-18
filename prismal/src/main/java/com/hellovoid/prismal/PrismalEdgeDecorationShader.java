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
                
                    // Upstream Prismal already uses u_lightDir for OS4 / Blinn-Phong light.
                    // The same direction controls the cast shadow and directional rim.
                    // Outside the silhouette, avoid *all* refraction and backdrop sampling.
                    if (distMask > 0.0) {
                        float stroke = 0.0;
                        float lighting = 1.0;
                        if (u_outerStrokeColor.a > 0.001 && u_outerStrokeWidthPx > 0.0) {
                            vec2 sdfNormal = gradSdRoundedRectRealistic(cKy, halfSz, radCorner);
                            vec2 normal = normalize(vec2(sdfNormal.x, -sdfNormal.y)
                                    + vec2(1e-5));
                            vec2 light = normalize(u_lightDir + vec2(1e-4));
                            lighting = mix(1.0, 0.82 + 0.36 * max(dot(normal, light), 0.0),
                                    u_strokeLightResponse);
                            stroke = u_outerStrokeColor.a * lighting
                                    * (1.0 - smoothstep(0.0, u_outerStrokeWidthPx,
                                            distMask));
                        }
                        float shadow = 0.0;
                        if (u_dropShadowColor.a > 0.001 && u_dropShadowRadiusPx > 0.0) {
                            vec2 light = normalize(u_lightDir + vec2(1e-4));
                            vec2 cast = -light * u_dropShadowRadiusPx * 0.42
                                    * u_dropShadowLightFollow;
                            // Android UI +Y points downward, while Prismal GLSL +Y points up.
                            vec2 offset = cast + vec2(0.0, -u_dropShadowOffsetYPx);
                            float shadowDist = sdRoundBox(pPx - offset,
                                    halfSz, crMask, u_sminSmoothing);
                            float sigma = max(u_dropShadowRadiusPx, 0.1);
                            float ambient = exp(-0.5 * pow(max(shadowDist, 0.0) / sigma, 2.0));
                            float contact = exp(-0.5 * pow(max(shadowDist, 0.0)
                                    / max(sigma * 0.25, 0.5), 2.0));
                            shadow = u_dropShadowColor.a * (0.72 * ambient + 0.28 * contact);
                        }
                        float a = clamp(stroke + shadow * (1.0 - stroke), 0.0, 1.0);
                        if (a < 0.002) discard;
                        vec3 rgb = (u_outerStrokeColor.rgb * stroke
                                + u_dropShadowColor.rgb * shadow * (1.0 - stroke))
                                / max(a, 0.002);
                        gl_FragColor = vec4(rgb, a * u_transmittance);
                        return;
                    }
                """)
                .replace(FINAL, """
                    if (u_outerStrokeColor.a > 0.001 && u_outerStrokeWidthPx > 0.0) {
                        vec2 sdfNormal = gradSdRoundedRectRealistic(cKy, halfSz, radCorner);
                        vec2 normal = normalize(vec2(sdfNormal.x, -sdfNormal.y)
                                + vec2(1e-5));
                        vec2 light = normalize(u_lightDir + vec2(1e-4));
                        float facing = max(dot(normal, light), 0.0);
                        float lighting = mix(1.0, 0.82 + 0.36 * facing,
                                u_strokeLightResponse);
                        float insideStroke = 1.0 - smoothstep(
                                0.0, u_outerStrokeWidthPx, -distMask);
                        color = mix(color, u_outerStrokeColor.rgb,
                                clamp(u_outerStrokeColor.a * lighting * insideStroke, 0.0, 1.0));
                    }
                    """ + FINAL);
    }
}
