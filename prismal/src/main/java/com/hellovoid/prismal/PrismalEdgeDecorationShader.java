package com.hellovoid.prismal;

/**
 * Optical response of one rounded glass volume. The upstream OS4 Fresnel,
 * reflection, directional highlight, AA and rounded SDF remain untouched.
 * In one draw, tint the narrow SDF contour using refracted background RGB and
 * the original OS4 highlight color. Light continuously changes its color from
 * dark material to bright reflected material, rather than hiding a black line.
 * No additional pass/Surface/texture.
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

                    float opticalAlpha = 0.0;
                    float opticalDark = 0.0;
                    float opticalSpec = 0.0;
                    float sharpWeight = 0.0;
                    float innerMeniscusShade = 0.0;
                    vec3 edgeTransmission = vec3(0.0);
                    float insidePx = max(-surfaceDistance, 0.0);
                    // Extent=0 means no rim even if this optional program
                    // is selected to draw only its independent cast shadow.
                    if (u_backlitStrength > 0.0 && u_backlitWidthPx > 0.0
                            && u_backlitSideStart < 1.0) {
                        float aa = max(edgeAa, 0.75);
                        float coreHalfPx = max(0.55, u_backlitWidthPx * 0.28);
                        float coreCoverage = 1.0 - smoothstep(
                                coreHalfPx, coreHalfPx + aa, abs(surfaceDistance));
                        // High alpha at d=0 removes the transparent seam.
                        // Strength controls reflected energy independently
                        // of the underlying silhouette's geometric coverage.
                        opticalAlpha = coreCoverage
                                * mix(0.92, 0.99, u_backlitStrength)
                                * smoothstep(0.0, 0.10, u_backlitStrength);

                        float sharpEndPx = clamp(aa * 4.0, 2.75, 4.5);
                        sharpWeight = 1.0 - smoothstep(
                                1.25, sharpEndPx, insidePx);
                        float meniscusWidthPx = clamp(
                                os4EdgeWidthPx * 0.55, 2.5, 5.0);
                        if (abs(surfaceDistance) < meniscusWidthPx
                                && surfaceDistance > -sharpEndPx
                                && (coreCoverage > 0.001 || sharpWeight > 0.001)) {
                            vec2 normal = normalize(os4SdfGradient + vec2(0.00001));
                            // Material orientation creates the two optical
                            // lobes: horizontal top/bottom specular and
                            // vertical left/right refractive darkness.
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
                            opticalSpec = horizontalWeight
                                    * mix(0.82, 1.0, direction01)
                                    * mix(0.72, 1.0,
                                            clamp(os4SoftLight * 1.6, 0.0, 1.0));

                            float rimReflectPx = u_os4ReflectOffsetPx > 0.0
                                    ? u_os4ReflectOffsetPx
                                    : clamp(u_glassThickness * 0.38, 4.0, 14.0);
                            float sizeCapPx = clamp(
                                    min(u_glassSize.x, u_glassSize.y) * 0.025,
                                    3.0, 9.0);
                            float bendPeakPx = min(sizeCapPx,
                                    max(u_lensRefractionPx, 0.0) * 0.14
                                    + rimReflectPx * 0.35);
                            float opticalMeniscusProfile = 1.0 - smoothstep(
                                    0.0, meniscusWidthPx, abs(surfaceDistance));
                            float bendPx = bendPeakPx
                                    * mix(0.35, 1.0, opticalMeniscusProfile);
                            vec2 rimUv = backdropUv(v_screenTexCoord,
                                    -normal * bendPx / u_resolution, 0.0);
                            edgeTransmission = texture2D(u_backgroundTexture, rimUv).rgb;

                            // A shallow, broad side meniscus replaces a
                            // second dark hairline glued inside the surface.
                            float innerShade = smoothstep(0.0, 0.65, insidePx)
                                    * (1.0 - smoothstep(1.5, 3.5, insidePx));
                            innerMeniscusShade = 0.10 * opticalDark * innerShade;
                        }
                    }

                    // The optional program can still cast a shadow when all
                    // rim parameters are disabled. Straight-alpha compositing
                    // is performed in one fragment, without a new FBO.
                    if (opacity < 0.001) {
                        float combinedAlpha = opticalAlpha
                                + exteriorShadowAlpha * (1.0 - opticalAlpha);
                        if (combinedAlpha < 0.001) discard;
                        vec3 rimRgb = shadeOpticalMeniscus(
                                edgeTransmission, opticalDark, opticalSpec);
                        vec3 premul = rimRgb * opticalAlpha
                                + u_castShadowColor.rgb * exteriorShadowAlpha
                                * (1.0 - opticalAlpha);
                        gl_FragColor = vec4(
                                premul / max(combinedAlpha, 0.0001), combinedAlpha);
                        return;
                    }
                    """)
                .replace(FINAL, """
                    // Continuous sharp-to-body handoff by PHYSICAL distance,
                    // not by opacity (which changes with the glass silhouette).
                    vec3 innerGlassRgb = color;
                    if (sharpWeight > 0.001) {
                        innerGlassRgb = mix(innerGlassRgb, edgeTransmission,
                                sharpWeight * 0.16);
                    }
                    innerGlassRgb *= 1.0 - innerMeniscusShade;

                    float glassAlpha = opacity * u_transmittance;
                    vec3 rimBase = mix(color, edgeTransmission, sharpWeight);
                    vec3 rimRgb = shadeOpticalMeniscus(
                            rimBase, opticalDark, opticalSpec);
                    // One optical core centered at the real SDF zero-crossing:
                    // the former outer stroke and inner glint no longer meet
                    // at d=0 with a transparent notch or positional gap.
                    float coreAndGlassAlpha = opticalAlpha
                            + glassAlpha * (1.0 - opticalAlpha);
                    vec3 coreAndGlassPremul = rimRgb * opticalAlpha
                            + innerGlassRgb * glassAlpha * (1.0 - opticalAlpha);
                    float finalAlpha = coreAndGlassAlpha
                            + exteriorShadowAlpha * (1.0 - coreAndGlassAlpha);
                    vec3 finalPremul = coreAndGlassPremul
                            + u_castShadowColor.rgb * exteriorShadowAlpha
                            * (1.0 - coreAndGlassAlpha);
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
