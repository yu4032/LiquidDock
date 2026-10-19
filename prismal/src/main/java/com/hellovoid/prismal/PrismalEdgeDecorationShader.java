package com.hellovoid.prismal;

/**
 * Optical response of one rounded glass volume. The upstream OS4 Fresnel,
 * reflection, directional highlight, AA and rounded SDF remain untouched.
 * In the SAME draw, draw a true, narrow SDF-constrained dark absorption line
 * only where the OS4 directional irradiance is weak, and cast a whole-body
 * low-frequency shadow beyond the silhouette. This is not a broad tint/overlay:
 * its subpixel/physical-pixel width is tied to the original alpha contour.
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
                    // The narrow, high-contrast absorption line is a real
                    // SDF contour, not broad backlight color attenuation.
                    // OS4 has already evaluated both primary/secondary
                    // directionality and angular falloff as os4Directional.
                    if (u_backlitStrength > 0.0 && u_backlitWidthPx > 0.0
                            && u_os4EdgeEnabled > 0.5) {
                        float lightReference = max(
                                max(u_os4DirectionalIntensity,
                                        u_os4DirectionalOppositeIntensity), 0.0);
                        if (lightReference > 0.001) {
                            float relativeLight = clamp(
                                    os4Directional / lightReference, 0.0, 1.0);
                            // Smoothly extinguish the line on illuminated
                            // edges; no fixed left/right mask, sign test or
                            // two-ended line segment to introduce hard caps.
                            float darkness = 1.0 - smoothstep(0.0,
                                    max(u_backlitSoftness, 0.05), relativeLight);
                            float taper = darkness * darkness
                                    * (3.0 - 2.0 * darkness);
                            float lineWidthPx = u_backlitWidthPx * taper;
                            // Use the SAME shifted silhouette as Prismal's
                            // glass alpha; positive distance is inside glass.
                            float insideDistance = edgeDist - insetShiftPx;
                            float aa = max(edgeAa * 0.5, 0.25);
                            float innerBand = smoothstep(-aa, aa, insideDistance)
                                    * (1.0 - smoothstep(
                                            max(lineWidthPx - aa, 0.0),
                                            lineWidthPx + aa, insideDistance));
                            float lineOpacity = clamp(
                                    u_backlitStrength * taper * innerBand,
                                    0.0, 0.98);
                            // A visible dark physical contour (near-black),
                            // whose position, thickness and opacity all follow
                            // local light; never changes the silhouette alpha.
                            color = mix(color, vec3(0.015), lineOpacity);
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
