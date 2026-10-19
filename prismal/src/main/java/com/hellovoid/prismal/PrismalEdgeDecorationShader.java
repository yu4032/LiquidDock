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
                // Dock optical rim is adapted to the native output viewport,
                // without moving or shrinking the original glass silhouette.
                uniform float u_backlitViewportInsetPx;
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

                // Two-stage refractive lobe and counter-light response follow
                // the independently documented macOS 27 glass math in the
                // MIT-licensed medfa12/liquid-glass-react-native reference.
                // This version deliberately reuses Prismal's existing texture,
                // SDF, uniform layout, and zero-copy PassBlur source.
                float opticalLensArc(float t) {
                    t = clamp(t, 0.0, 1.0);
                    return sqrt(max((2.0 - t) * t, 0.0));
                }

                float opticalOpposingFill(float facing, float keyIntensity,
                        float fillIntensity) {
                    float key = opticalSmoother01((facing - 0.06) / 0.94);
                    float fill = opticalSmoother01((-facing - 0.06) / 0.94);
                    // Opposing soft knees are summed, not chosen by a sign
                    // switch; bottom reflection survives when fill=0.
                    float keyKnee = key / (1.0 + 0.35 * (1.0 - key));
                    float fillKnee = fill / (1.0 + 0.35 * (1.0 - fill));
                    return clamp(
                            keyKnee * (0.72 + 0.24 * clamp(keyIntensity, 0.0, 1.0))
                            + fillKnee * (0.66 + 0.20 * clamp(fillIntensity, 0.0, 1.0)),
                            0.0, 1.0);
                }

                // Degree-seven odd polynomial approximation of erfc(x)/2,
                // adapted from the QuartzCore reconstruction in
                // medfa12/liquid-glass-react-native (MIT). The difference
                // between two soft steps is a narrow analytic shadow band:
                // no blur pass, mip chain or texture allocation required.
                float opticalErfcHalf(float distance) {
                    float u = clamp(0.25 * distance + 0.5, 0.0, 1.0);
                    float x = 4.0 * u - 2.0;
                    float x2 = x * x;
                    float p = 0.002954;
                    p = p * x2 - 0.034460;
                    p = p * x2 + 0.168210;
                    p = p * x2 - 0.560550;
                    return clamp(p * x + 0.5, 0.0, 1.0);
                }

                vec3 shadeOpticalMeniscus(vec3 transmitted, float darkWeight,
                        float specEnergy) {
                    float y = dot(transmitted, vec3(0.2126, 0.7152, 0.0722));
                    float darkFloor = mix(0.46, 0.28,
                            smoothstep(0.18, 0.72, y));
                    // The physical outline is continuous: only its COLOR
                    // changes from dark refracted hue to bright specular
                    // reflection. Never let the dark-lobe fade kill coverage.
                    float transition = opticalSmoother01(
                            1.0 - clamp(darkWeight, 0.0, 1.0));
                    vec3 darkRim = transmitted * darkFloor;
                    vec3 brightRim = mix(transmitted, OPTICAL_SPECULAR_RGB,
                            clamp(0.68 + 0.23 * specEnergy, 0.0, 0.94));
                    vec3 contour = mix(darkRim, brightRim, transition);
                    // Reconstructed edge-bleed principle: retain a thin
                    // reflection response through the midtone crossing,
                    // where a simple dark-to-light lerp can match wallpaper.
                    float midGlint = 0.15 * 4.0 * transition * (1.0 - transition);
                    return clamp(mix(contour, OPTICAL_SPECULAR_RGB, midGlint),
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
                        // Shift only the outer contour's coverage inward on
                        // clipped native Dock output. The SDF, glass body,
                        // original Fresnel, inner glint and shadows stay put.
                        float outlineDistance = surfaceDistance
                                + u_backlitViewportInsetPx;
                        // The outer contour starts just inside the AA footprint
                        // so there is no transparent gap at the native silhouette.
                        // The peak and majority of its coverage remain OUTSIDE.
                        float outlineEntry = smoothstep(
                                -aa * 0.70, aa * 0.20, outlineDistance);
                        float outlineExit = 1.0 - smoothstep(
                                max(outlineWidthPx - aa * 0.5, aa * 0.5),
                                outlineWidthPx + aa * 0.5, outlineDistance);
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
                            // Independent +normal / -normal key and fill.
                            // This remains a secondary response to material
                            // orientation, so top AND bottom stay reflective.
                            float opposingFill = opticalOpposingFill(
                                    signedFacing, u_os4DirectionalIntensity,
                                    u_os4DirectionalOppositeIntensity);
                            opticalDark = sideWeight * mix(1.0, 0.88, opposingFill);
                            opticalSpec = horizontalWeight
                                    * mix(0.72, 1.0, opposingFill);
                            // A distinct internal highlight, not a different
                            // RGB choice for the same outer-outline alpha.
                            innerHighlightAlpha = clamp(
                                    u_backlitStrength * innerBand
                                    * horizontalWeight * mix(0.26, 0.46, opposingFill),
                                    0.0, 0.52);

                            // The outer contour and inner edge-bleed share one
                            // displaced unblurred source tap per edge fragment.
                            // Body/shadow-only fragments remain tap-free.
                            if (outlineAlpha > 0.001
                                    || innerHighlightAlpha > 0.001) {
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
                                // Two refractive lobe profiles, in the same
                                // physical SDF distance units as the glass.
                                // A tiny AA offset avoids sqrt'(0) singularity.
                                float innerT = clamp(
                                        (max(-surfaceDistance, 0.0) + aa * 0.15)
                                        / meniscusWidthPx, 0.0, 1.0);
                                float outerT = clamp(
                                        (max(surfaceDistance, 0.0) + aa * 0.15)
                                        / meniscusWidthPx, 0.0, 1.0);
                                float innerBendPx = max(u_lensRefractionPx, 0.0)
                                        * 0.14 * (1.0 - opticalLensArc(innerT));
                                float outerBendPx = rimReflectPx * 0.35
                                        * (1.0 - opticalLensArc(outerT));
                                // Continuous outer/inner handoff straddles the
                                // same SDF zero crossing as the two alpha bands.
                                float refractionHandoff = smoothstep(
                                        -aa * 0.65, aa * 0.65, surfaceDistance);
                                float bendPx = min(sizeCapPx,
                                        mix(innerBendPx, outerBendPx, refractionHandoff)
                                        * mix(0.35, 1.0, opticalMeniscusProfile));
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
                    // macOS27-inspired analytic ring shadow and holding tone
                    // live INSIDE the SDF. They never create a second external
                    // Dock cast shadow (MiShadow remains the only native owner).
                    // Apply to the original glass color before its independent
                    // inner mirror highlight; the highlight is not attenuated.
                    vec3 ringShadedGlass = color;
                    // Adapt macOS27's edge-bleed ramp without a second blur
                    // texture. The prepared unblurred refracted edge sample
                    // transitions to the already-computed full Prismal blur,
                    // restricted to the physical inner meniscus. This band
                    // remains underneath the independent inner glint.
                    if (innerHighlightAlpha > 0.001 && surfaceDistance < 0.0) {
                        float bleedDepth = max(-surfaceDistance, 0.0);
                        float bleedRangePx = clamp(
                                u_backlitWidthPx * 2.0 + edgeAa, 2.0, 5.0);
                        float bleedBand = 1.0 - smoothstep(
                                0.0, bleedRangePx, bleedDepth);
                        vec3 bleedTint = mix(edgeTransmission,
                                OPTICAL_SPECULAR_RGB, opticalSpec * 0.40);
                        bleedTint *= mix(0.84, 1.0, opticalSpec);
                        ringShadedGlass = mix(ringShadedGlass, bleedTint,
                                bleedBand * 0.18 * u_backlitStrength);
                    }

                    float ringDepth = max(-surfaceDistance, 0.0);
                    float ringRangePx = min(12.0,
                            max(3.0, u_backlitWidthPx * 3.0 + edgeAa));
                    if (u_backlitStrength > 0.0 && u_backlitWidthPx > 0.0
                            && u_backlitSideStart < 1.0
                            && surfaceDistance < 0.0
                            && ringDepth < ringRangePx) {
                        float ringRadiusPx = clamp(
                                u_backlitWidthPx * 0.85 + edgeAa * 0.8,
                                1.0, 5.0);
                        float ringStrokePx = clamp(
                                u_backlitWidthPx * 0.70, 0.75, 4.0);
                        float invRing = 0.70710678 / ringRadiusPx;
                        float ringInner = opticalErfcHalf(ringDepth * invRing);
                        float ringOuter = opticalErfcHalf(
                                (ringDepth + ringStrokePx) * invRing);
                        float ringAmount = clamp(
                                (ringInner - ringOuter) * 0.58
                                * u_backlitStrength * opticalDark,
                                0.0, 0.24);
                        ringShadedGlass *= 1.0 - ringAmount;

                        // The reference macOS27 holding-tone curve desaturates
                        // a shallow near-edge region using the same erfc family.
                        // Reuse already computed RGB rather than a new backdrop
                        // read; the body blur and per-scope tint are unchanged.
                        float hold = opticalErfcHalf(
                                (ringDepth - ringStrokePx * 1.5) * invRing);
                        float luma = dot(ringShadedGlass,
                                vec3(0.212646, 0.715332, 0.072205));
                        ringShadedGlass = mix(ringShadedGlass, vec3(luma),
                                clamp(hold * 0.14 * u_backlitStrength
                                        * opticalDark, 0.0, 0.14));
                    }

                    // Preserve the native OS4 highlight. Source-over order:
                    // glass/ring -> independent INNER glint -> OUTER contour ->
                    // underlying cast shadow. Neither optical layer is a
                    // different RGB choice for the other layer's alpha.
                    float glassAlpha = opacity * u_transmittance;
                    float glassAndGlintAlpha = innerHighlightAlpha
                            + glassAlpha * (1.0 - innerHighlightAlpha);
                    vec3 glassAndGlintPremul =
                            OPTICAL_SPECULAR_RGB * innerHighlightAlpha
                            + ringShadedGlass * glassAlpha * (1.0 - innerHighlightAlpha);

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
                    // macOS26 measured post-coverage rim glint: a narrow
                    // exponential reflection is evaluated AFTER coverage so
                    // translucent silhouette pixels do not swallow the glint.
                    // Only the inner surface may emit it; never overwrite the
                    // exterior refracted contour or the separate OS4 highlight.
                    vec3 finalRgb = finalPremul / max(finalAlpha, 0.0001);
                    if (u_backlitStrength > 0.0
                            && innerHighlightAlpha > 0.001
                            && surfaceDistance < 0.0) {
                        float glintTauPx = clamp(
                                u_backlitWidthPx * 0.55 + 0.65, 0.85, 2.0);
                        float rimGlint = exp(-abs(surfaceDistance) / glintTauPx);
                        float rimGlintGain = clamp(
                                0.10 * u_backlitStrength * rimGlint
                                * (1.0 - opticalDark) * (1.0 - outlineAlpha)
                                / max(finalAlpha, 0.25), 0.0, 0.20);
                        finalRgb = min(vec3(1.0), finalRgb + vec3(rimGlintGain));
                    }
                    gl_FragColor = vec4(finalRgb, finalAlpha);
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
