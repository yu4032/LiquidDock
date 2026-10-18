package com.hellovoid.prismal;

/**
 * Optical response of one rounded glass volume. The upstream OS4 Fresnel,
 * reflection, directional highlight, AA and rounded SDF remain untouched.
 * In the SAME draw, shade the under-illuminated rim and cast a whole-body
 * low-frequency shadow beyond the silhouette. Never paint a black stroke.
 */
final class PrismalEdgeDecorationShader {
    private static final String PRECISION = "precision highp float;";
    private static final String BEFORE_DISCARD = "if (opacity < 0.001) discard;";
    private static final String FINAL =
            "gl_FragColor = vec4(color, opacity * u_transmittance);";

    private PrismalEdgeDecorationShader() {}

    static String apply(String shader) {
        requireOnce(shader, PRECISION);
        requireOnce(shader, BEFORE_DISCARD);
        requireOnce(shader, FINAL);
        if (!shader.contains("vec2 os4SdfGradient = vec2(")
                || !shader.contains("float edgeAa = max(fwidth(distMask), 0.75);")
                || !shader.contains("float edgeDist = -distMask;")
                || !shader.contains("uniform vec2 u_invariantLightXY;")
                || !shader.contains("uniform float u_os4DirectionalIntensity;")
                || !shader.contains("uniform float u_os4DirectionalOppositeIntensity;")) {
            throw new IllegalStateException("Prismal OS4 optics/light contract changed");
        }
        return shader.replace(PRECISION, PRECISION + """
                uniform float u_backlitWidthPx;
                uniform float u_backlitStrength;
                uniform float u_backlitSoftness;
                uniform vec4 u_castShadowColor;
                uniform float u_castShadowRadiusPx;
                uniform float u_castShadowOffsetYPx;
                uniform float u_castShadowLightFollow;
                """)
                .replace(BEFORE_DISCARD, """
                    // Derivative-dependent OS4 SDF/normal/AA have been evaluated
                    // before this path. Exterior fragments avoid new captures.
                    if (distMask > 0.0 && u_castShadowRadiusPx > 0.0
                            && u_castShadowColor.a > 0.0) {
                        vec2 n = normalize(os4SdfGradient + vec2(0.00001));
                        float facing = dot(n, u_invariantLightXY);
                        // First-order signed SDF translation of the full rounded
                        // silhouette; no second rounded-box SDF or blur pass.
                        float projected = -facing * u_castShadowRadiusPx * 0.42
                                * u_castShadowLightFollow
                                - n.y * u_castShadowOffsetYPx;
                        float distanceToCaster = max(0.0, distMask - projected);
                        float halo = 1.0 - smoothstep(0.0,
                                max(u_castShadowRadiusPx * 2.0, 0.001),
                                distanceToCaster);
                        float shadowAlpha = u_castShadowColor.a * halo * halo;
                        if (shadowAlpha > 0.002) {
                            gl_FragColor = vec4(u_castShadowColor.rgb,
                                    shadowAlpha * u_transmittance);
                            return;
                        }
                    }
                    """ + BEFORE_DISCARD)
                .replace(FINAL, """
                    // Light-facing high gloss is provided by upstream Prismal
                    // OS4. Only attenuate the other half of the SAME optical
                    // edge, with continuous irradiance and a soft width falloff.
                    if (u_backlitStrength > 0.0 && u_backlitWidthPx > 0.0
                            && u_os4EdgeEnabled > 0.5) {
                        vec2 n = normalize(os4SdfGradient + vec2(0.00001));
                        float facing = dot(n, u_invariantLightXY);
                        float key = max(u_os4DirectionalIntensity, 0.0);
                        float fill = max(u_os4DirectionalOppositeIntensity, 0.0);
                        float reference = max(key, fill);
                        if (reference > 0.001) {
                            float localLight = key * max(facing, 0.0)
                                    + fill * max(-facing, 0.0);
                            float deficit = clamp(
                                    (reference - localLight) / reference, 0.0, 1.0);
                            float transition = smoothstep(
                                    0.0, max(u_backlitSoftness, 0.05), deficit);
                            float edgeBand = os4EdgeBand(
                                    edgeDist, u_backlitWidthPx, edgeAa);
                            float darken = clamp(transition * edgeBand
                                    * u_backlitStrength
                                    * clamp(reference, 0.0, 1.0) * 0.34,
                                    0.0, 0.22);
                            color *= 1.0 - darken;
                        }
                    }
                    """ + FINAL);
    }

    private static void requireOnce(String source, String token) {
        if (source == null) throw new IllegalArgumentException("shader == null");
        int i = source.indexOf(token);
        if (i < 0 || source.indexOf(token, i + token.length()) >= 0) {
            throw new IllegalStateException("Prismal optical shader contract: " + token);
        }
    }
}
