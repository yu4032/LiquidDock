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
                    // Keep separate alpha until the final, same-fragment blend:
                    // a translucent glass surface must NOT wash out the line.
                    float rimLineCoverage = 0.0;
                    // OS4's 3D face illumination carries a positive Z term,
                    // so using os4Directional here makes even grazing side
                    // contours appear lit and collapses the ink-alpha to zero.
                    // Measure illumination along the 2D SDF edge normal instead.
                    // This does not hardcode which sides of the glass are dark.
                    // Edge absorption is deliberately independent of the
                    // optional OS4 reflective-highlight visibility mask.
                    if (u_backlitStrength > 0.0 && u_backlitWidthPx > 0.0) {
                        float keyLight = max(u_os4DirectionalIntensity, 0.0);
                        float fillLight = max(u_os4DirectionalOppositeIntensity, 0.0);
                        float lightEnergy = clamp(
                                (keyLight + fillLight) * 2.0, 0.0, 1.0);
                        if (lightEnergy > 0.001) {
                            vec2 edgeNormal = normalize(
                                    os4SdfGradient + vec2(0.00001));
                            float angularIncidence = abs(
                                    dot(edgeNormal, u_invariantLightXY));
                            // At the stock (-0.50,-0.80) light direction
                            // side-edge incidence is ~0.53, top/bottom ~0.85.
                            // A continuous grazing-angle falloff preserves a
                            // real side line and extinguishes top/bottom rims.
                            float fadeStart = mix(
                                    0.30, 0.65, u_backlitSoftness);
                            float fadeEnd = min(fadeStart + 0.30, 0.99);
                            float darkness = 1.0 - smoothstep(
                                    fadeStart, fadeEnd, angularIncidence);
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
                                    u_backlitStrength * lightEnergy * taper * innerBand,
                                    0.0, 0.98);
                            // Real pigment-like line: unlike mere color
                            // darkening, its coverage can remain visible when
                            // the glass body is highly transmissive.
                            rimLineCoverage = lineOpacity;
                        }
                    }
                    if (rimLineCoverage > 0.001) {
                        // Straight-alpha "line over glass" at the SAME output
                        // fragment. The line still fades with the glass SDF's
                        // silhouette opacity, so no outside halo is created.
                        float glassAlpha = opacity * u_transmittance;
                        float lineAlpha = opacity * rimLineCoverage;
                        float combinedAlpha = lineAlpha
                                + glassAlpha * (1.0 - lineAlpha);
                        vec3 combinedColor = (
                                vec3(0.015) * lineAlpha
                                + color * glassAlpha * (1.0 - lineAlpha))
                                / max(combinedAlpha, 0.0001);
                        gl_FragColor = vec4(combinedColor, combinedAlpha);
                        return;
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
