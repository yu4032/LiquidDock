package com.hellovoid.prismal;

/**
 * Additional optical material for a rounded glass volume. Preserve the
 * existing upstream OS4 Fresnel and highlight color. A separate inner glint
 * and an outer refracted contour share the exact native SDF silhouette,
 * then composite as independent alpha layers in one shader pass.
 * No additional Surface, texture allocation or capture.
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
                uniform float u_backlitSideStart;
                uniform float u_backlitSideSpan;
                uniform vec4 u_castShadowColor;
                uniform float u_castShadowRadiusPx;
                uniform float u_castShadowOffsetYPx;
                uniform float u_castShadowLightFollow;

                const vec3 OPTICAL_SPECULAR_RGB = vec3(0.98, 0.992, 1.0);

                // These are material responses, not paint strokes. The same
                // refracted RGB is attenuated at vertical edges and reflected
                // at horizontal edges. Directional OS4 light modulates both.
                float opticalSmoother01(float t) {
                    t = clamp(t, 0.0, 1.0);
                    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
                }

                vec3 shadeOpticalMeniscus(vec3 transmitted, float darkWeight,
                        float specEnergy) {
                    float y = dot(transmitted, vec3(0.2126, 0.7152, 0.0722));
                    float darkFloor = mix(0.46, 0.28,
                            smoothstep(0.18, 0.72, y));
                    float transmission = mix(1.0, darkFloor, darkWeight);
                    float reflection = (0.10 + 0.70 * specEnergy)
                            * (1.0 - darkWeight);
                    return clamp(transmitted * transmission * (1.0 - reflection)
                            + OPTICAL_SPECULAR_RGB * reflection,
                            vec3(0.0), vec3(1.0));
                }
                """)
                .replace(BEFORE_DISCARD, """
                    // Shadow is the only separate *underlay*. All optical rim
                    // energy is centered on this exact same opacity/SDF
                    // silhouette, not a shifted Dock-specific outer stroke.
                    float surfaceDistance = distMask + insetShiftPx;
                    float exteriorShadowAlpha = 0.0;
                    if (surfaceDistance > 0.0 && u_castShadowRadiusPx > 0.0
                            && u_castShadowColor.a > 0.0) {
                        vec2 shadowNormal = normalize(os4SdfGradient + vec2(0.00001));
                        float facing = dot(shadowNormal, u_invariantLightXY);
                        float projected = -facing * u_castShadowRadiusPx * 0.42
                                * u_castShadowLightFollow
                                - shadowNormal.y * u_castShadowOffsetYPx;
                        float distanceToCaster = max(0.0, surfaceDistance - projected);
                        float halo = 1.0 - smoothstep(0.0,
                                max(u_castShadowRadiusPx * 2.0, 0.001),
                                distanceToCaster);
                        exteriorShadowAlpha = u_castShadowColor.a * halo * halo
                                * u_transmittance;
                    }

                    // Both bands use the exact same SDF zero crossing.
                    // Outside: refracted contour. Inside: mirror highlight.
                    // Neither is a displaced or independently fitted outline.
                    float outlineAlpha = 0.0;
                    float innerHighlightAlpha = 0.0;
                    float opticalDark = 0.0;
                    float opticalSpec = 0.0;
                    vec3 edgeTransmission = vec3(0.0);
                    if (u_backlitStrength > 0.0 && u_backlitWidthPx > 0.0
                            && u_backlitSideStart < 1.0) {
                        float aa = max(edgeAa, 0.75);
                        float outlineWidthPx = max(u_backlitWidthPx, 0.5);
                        // The outer contour starts just inside the AA footprint
                        // so there is no transparent gap at the native silhouette.
                        // The peak and majority of its coverage remain OUTSIDE.
                        float outlineEntry = smoothstep(
                                -aa * 0.70, aa * 0.20, surfaceDistance);
                        float outlineExit = 1.0 - smoothstep(
                                max(outlineWidthPx - aa * 0.5, aa * 0.5),
                                outlineWidthPx + aa * 0.5, surfaceDistance);
                        outlineAlpha = clamp(
                                u_backlitStrength * outlineEntry * outlineExit,
                                0.0, 0.98);

                        // The internal glint has its own envelope and alpha.
                        // It sits immediately INSIDE the contour, preserving
                        // Prismal's original OS4 reflected light underneath.
                        float highlightWidthPx = clamp(
                                outlineWidthPx * 0.70 + aa * 0.65, 1.0, 3.5);
                        float innerBand = smoothstep(
                                -highlightWidthPx - aa * 0.6,
                                -highlightWidthPx + aa * 0.30, surfaceDistance)
                                * (1.0 - smoothstep(
                                        -aa * 0.10, aa * 0.75, surfaceDistance));

                        if (outlineAlpha > 0.001 || innerBand > 0.001) {
                            vec2 normal = normalize(os4SdfGradient + vec2(0.00001));
                            float ax = abs(normal.x);
                            float ay = abs(normal.y);
                            float sideAxis = ax / max(ax + ay, 0.0001);
                            float sideT = (sideAxis - u_backlitSideStart)
                                    / max(u_backlitSideSpan, 0.001);
                            float sideWeight = opticalSmoother01(sideT);
                            float horizontalWeight = 1.0 - sideWeight;
                            float signedFacing = dot(normal, u_invariantLightXY);
                            float direction01 = 0.5 + 0.5 * signedFacing;
                            opticalDark = sideWeight * mix(1.0, 0.88, direction01);
                            // The original OS4 soft-light locals are declared
                            // after the discard. Only uniform light values
                            // may be consulted at this earlier stage.
                            float directionalFill = clamp(
                                    max(u_os4DirectionalIntensity, 0.0) * 0.55
                                    + max(u_os4DirectionalOppositeIntensity, 0.0) * 0.25,
                                    0.0, 1.0);
                            opticalSpec = horizontalWeight
                                    * mix(0.82, 1.0, direction01)
                                    * mix(0.72, 1.0, directionalFill);
                            // A distinct internal highlight, not a different
                            // RGB choice for the same outer-outline alpha.
                            innerHighlightAlpha = clamp(
                                    u_backlitStrength * innerBand
                                    * horizontalWeight * mix(0.24, 0.46, direction01),
                                    0.0, 0.52);

                            // Only the outside contour requires a sharp backdrop
                            // tap. No extra read for an inner highlight or shadow.
                            if (outlineAlpha > 0.001) {
                                float rimReflectPx = u_os4ReflectOffsetPx > 0.0
                                        ? u_os4ReflectOffsetPx
                                        : clamp(u_glassThickness * 0.38, 4.0, 14.0);
                                float sizeCapPx = clamp(
                                        min(u_glassSize.x, u_glassSize.y) * 0.025,
                                        3.0, 9.0);
                                float bendPeakPx = min(sizeCapPx,
                                        max(u_lensRefractionPx, 0.0) * 0.14
                                        + rimReflectPx * 0.35);
                                float meniscusWidthPx = clamp(
                                        os4EdgeWidthPx * 0.55, 2.5, 5.0);
                                float opticalMeniscusProfile = 1.0 - smoothstep(
                                        0.0, meniscusWidthPx, abs(surfaceDistance));
                                float bendPx = bendPeakPx
                                        * mix(0.35, 1.0, opticalMeniscusProfile);
                                vec2 rimUv = backdropUv(v_screenTexCoord,
                                        -normal * bendPx / u_resolution, 0.0);
                                edgeTransmission =
                                        texture2D(u_backgroundTexture, rimUv).rgb;
                            }
                        }
                    }

                    // Outside the original silhouette no glass body is shaded.
                    // Outer refraction still renders over the independent cast
                    // shadow. The inner highlight never draws in this branch.
                    if (opacity < 0.001) {
                        float combinedAlpha = outlineAlpha
                                + exteriorShadowAlpha * (1.0 - outlineAlpha);
                        if (combinedAlpha < 0.001) discard;
                        vec3 outlineRgb = shadeOpticalMeniscus(
                                edgeTransmission, opticalDark, opticalSpec);
                        vec3 premul = outlineRgb * outlineAlpha
                                + u_castShadowColor.rgb * exteriorShadowAlpha
                                * (1.0 - outlineAlpha);
                        gl_FragColor = vec4(
                                premul / max(combinedAlpha, 0.0001), combinedAlpha);
                        return;
                    }
                    """)
                .replace(FINAL, """
                    // Preserve Prismal's native optical highlight in 'color'.
                    // Source-over order: glass -> independent INNER glint ->
                    // OUTER refracted contour -> underlying cast shadow.
                    // The glint's alpha does not depend on the outline's alpha.
                    float glassAlpha = opacity * u_transmittance;
                    float glassAndGlintAlpha = innerHighlightAlpha
                            + glassAlpha * (1.0 - innerHighlightAlpha);
                    vec3 glassAndGlintPremul =
                            OPTICAL_SPECULAR_RGB * innerHighlightAlpha
                            + color * glassAlpha * (1.0 - innerHighlightAlpha);

                    vec3 outlineRgb = shadeOpticalMeniscus(
                            edgeTransmission, opticalDark, opticalSpec);
                    float glassWithOutlineAlpha = outlineAlpha
                            + glassAndGlintAlpha * (1.0 - outlineAlpha);
                    vec3 glassWithOutlinePremul = outlineRgb * outlineAlpha
                            + glassAndGlintPremul * (1.0 - outlineAlpha);

                    float finalAlpha = glassWithOutlineAlpha
                            + exteriorShadowAlpha * (1.0 - glassWithOutlineAlpha);
                    vec3 finalPremul = glassWithOutlinePremul
                            + u_castShadowColor.rgb * exteriorShadowAlpha
                            * (1.0 - glassWithOutlineAlpha);
                    gl_FragColor = vec4(
                            finalPremul / max(finalAlpha, 0.0001), finalAlpha);
                    """);
    }

    private static void requireOnce(String source, String token) {
        if (source == null) throw new IllegalArgumentException("shader == null");
        int i = source.indexOf(token);
        if (i < 0 || source.indexOf(token, i + token.length()) >= 0) {
            throw new IllegalStateException("Prismal optical shader contract: " + token);
        }
    }
}
