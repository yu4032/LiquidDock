package com.hellovoid.prismal;

/** Optional outer edge paint in the same GPU draw as Prismal glass; no extra backdrop or pass. */
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
                uniform vec4 u_dropShadowColor;
                uniform float u_dropShadowRadiusPx;
                uniform float u_dropShadowOffsetYPx;
                """)
                .replace(DIST, DIST + """
                
                    // Only exterior fragments take this path. Keep the expensive refractive
                    // sampling entirely inside the original SDF contour.
                    if (distMask > 0.0) {
                        float stroke = 0.0;
                        if (u_outerStrokeColor.a > 0.001 && u_outerStrokeWidthPx > 0.0) {
                            stroke = u_outerStrokeColor.a * (1.0 - smoothstep(
                                    0.0, u_outerStrokeWidthPx, distMask));
                        }
                        float shadow = 0.0;
                        if (u_dropShadowColor.a > 0.001 && u_dropShadowRadiusPx > 0.0) {
                            float shiftedDistance = sdRoundBox(
                                    pPx - vec2(0.0, u_dropShadowOffsetYPx),
                                    halfSz, crMask, u_sminSmoothing);
                            float t = max(shiftedDistance, 0.0) / max(u_dropShadowRadiusPx, 0.1);
                            shadow = u_dropShadowColor.a * exp(-0.5 * t * t);
                        }
                        float a = stroke + shadow * (1.0 - stroke);
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
                        float insideStroke = 1.0 - smoothstep(
                                0.0, u_outerStrokeWidthPx, -distMask);
                        color = mix(color, u_outerStrokeColor.rgb,
                                u_outerStrokeColor.a * insideStroke);
                    }
                    """ + FINAL);
    }
}
