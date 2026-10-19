package com.hellovoid.prismal;

/**
 * Optical response of one rounded glass volume. The upstream OS4 Fresnel,
 * reflection, directional highlight, AA and rounded SDF remain untouched.
 * In the SAME draw, draw the narrow light-responsive ink line just OUTSIDE
 * the glass alpha silhouette; composite it beneath the existing OS4 highlights
 * and in front of the outer cast shadow. No additional pass/Surface/texture.
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
                uniform float u_backlitExtent;
                uniform float u_backlitViewportInsetPx;
                uniform vec4 u_castShadowColor;
                uniform float u_castShadowRadiusPx;
                uniform float u_castShadowOffsetYPx;
                uniform float u_castShadowLightFollow;
                """)
                .replace(BEFORE_DISCARD, """
                    // The shadow and ink line are UNDER the already-lit
                    // glass. A positive surfaceDistance is *outside* the
                    // exact alpha silhouette, including the inset shift.
                    // Computed before early discard, but after derivatives.
                    float surfaceDistance = distMask + insetShiftPx;
                    float exteriorShadowAlpha = 0.0;
                    if (surfaceDistance > 0.0 && u_castShadowRadiusPx > 0.0
                            && u_castShadowColor.a > 0.0) {
                        vec2 n = normalize(os4SdfGradient + vec2(0.00001));
                        float facing = dot(n, u_invariantLightXY);
                        float projected = -facing * u_castShadowRadiusPx * 0.42
                                * u_castShadowLightFollow
                                - n.y * u_castShadowOffsetYPx;
                        float distanceToCaster = max(0.0, surfaceDistance - projected);
                        float halo = 1.0 - smoothstep(0.0,
                                max(u_castShadowRadiusPx * 2.0, 0.001),
                                distanceToCaster);
                        // Preserve the prior exterior-shadow transmittance;
                        // moving the hairline must not brighten the full cast.
                        exteriorShadowAlpha = u_castShadowColor.a * halo * halo
                                * u_transmittance;
                    }

                    // Dock glass touches the native TextureView bounds. Move
                    // only the ink's sampling origin into its AA fringe; DO
                    // NOT change the Dock shape, corner radii or MiShadow.
                    // Other glass scopes keep a strictly external ink line.
                    float inkDistance = surfaceDistance + u_backlitViewportInsetPx;
                    float exteriorInkAlpha = 0.0;
                    if (inkDistance > 0.0 && u_backlitStrength > 0.0
                            && u_backlitWidthPx > 0.0) {
                        float keyLight = max(u_os4DirectionalIntensity, 0.0);
                        float fillLight = max(u_os4DirectionalOppositeIntensity, 0.0);
                        float lightEnergy = clamp(
                                (keyLight + fillLight) * 2.0, 0.0, 1.0);
                        if (lightEnergy > 0.001) {
                            vec2 edgeNormal = normalize(
                                    os4SdfGradient + vec2(0.00001));
                            float angularIncidence = abs(
                                    dot(edgeNormal, u_invariantLightXY));
                            // 0% reach is a TRUE zero-length line, not a faint
                            // all-around baseline. At low reach only nearly
                            // tangential normals show ink; 100% allows a thin
                            // continuous seam around the entire glass.
                            float grazing = clamp(1.0 - angularIncidence, 0.0, 1.0);
                            float reachStart = mix(1.0, -0.20, u_backlitExtent);
                            float progress = clamp((grazing - reachStart)
                                    / max(1.0 - reachStart, 0.001), 0.0, 1.0);
                            // Softness only changes the taper curve INSIDE the
                            // reach interval; it never extends its start point.
                            // 200% uses the full interval; 5% rises quickly.
                            float softness = clamp(u_backlitSoftness * 0.5, 0.0, 1.0);
                            float quick = smoothstep(0.0, 0.08, progress);
                            float gradual = smoothstep(0.0, 1.0, progress);
                            float taper = mix(quick, gradual, softness);
                            float e2 = u_backlitExtent * u_backlitExtent;
                            float e4 = e2 * e2;
                            taper = max(taper, 0.18 * e4 * e4);
                            float lineWidthPx = u_backlitWidthPx * taper;
                            float outsideAa = max(edgeAa * 0.5, 0.25);
                            float entry = smoothstep(0.0, outsideAa,
                                    inkDistance);
                            float exit = 1.0 - smoothstep(
                                    max(lineWidthPx - outsideAa, 0.0),
                                    lineWidthPx + outsideAa, inkDistance);
                            exteriorInkAlpha = clamp(
                                    u_backlitStrength * lightEnergy * taper
                                    * entry * exit, 0.0, 0.98);
                        }
                    }
                    // Ink is in front of the outer cast shadow, but BOTH are
                    // behind the illuminated glass surface (one fragment).
                    float underlayAlpha = exteriorInkAlpha
                            + exteriorShadowAlpha * (1.0 - exteriorInkAlpha);
                    vec3 underlayRgb = (vec3(0.015) * exteriorInkAlpha
                            + u_castShadowColor.rgb * exteriorShadowAlpha
                            * (1.0 - exteriorInkAlpha))
                            / max(underlayAlpha, 0.0001);
                    if (opacity < 0.001) {
                        if (underlayAlpha < 0.001) discard;
                        gl_FragColor = vec4(underlayRgb, underlayAlpha);
                        return;
                    }
                    """)
                .replace(FINAL, """
                    // Keep the original OS4-reflected glass OVER the exterior
                    // ink and shadow. This avoids removing the bright edge
                    // while retaining a visible hairline just outside it.
                    if (underlayAlpha > 0.001) {
                        float glassAlpha = opacity * u_transmittance;
                        float combinedAlpha = glassAlpha
                                + underlayAlpha * (1.0 - glassAlpha);
                        vec3 combinedRgb = (color * glassAlpha
                                + underlayRgb * underlayAlpha * (1.0 - glassAlpha))
                                / max(combinedAlpha, 0.0001);
                        gl_FragColor = vec4(combinedRgb, combinedAlpha);
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
