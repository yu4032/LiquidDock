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
                uniform float u_backlitGrazingStart;
                uniform float u_backlitGrazingSpan;
                uniform float u_backlitViewportInsetPx;
                uniform vec4 u_castShadowColor;
                uniform float u_castShadowRadiusPx;
                uniform float u_castShadowOffsetYPx;
                uniform float u_castShadowLightFollow;

                // Independent of the legacy hiSoft variable, which the
                // component gate declares only inside a conditional block.
                const vec3 OS4_OPTICAL_RIM_COLOR = vec3(0.98, 0.992, 1.008);

                // Source hue survives both sides of the rim. Only a small
                // specular energy term favors OS4's highlight palette, while
                // the dark side attenuates transmission without turning black.
                // Rim weights already contain the sole angular smoothstep.
                vec3 refractedContourColor(vec3 transmitted, vec3 highlight, float darkWeight) {
                    float lightEnergy = clamp(
                            (max(u_os4DirectionalIntensity, 0.0)
                             + max(u_os4DirectionalOppositeIntensity, 0.0)) * 2.0,
                            0.0, 1.0);
                    float transmission = mix(1.0, 0.12, darkWeight);
                    float specGain = (0.10 + 0.18 * lightEnergy) * (1.0 - darkWeight);
                    vec3 specTint = mix(transmitted, highlight, 0.25);
                    return clamp(transmitted * transmission + specTint * specGain,
                            vec3(0.0), vec3(1.0));
                }
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
                    float rimDarkWeight = 0.0;
                    float exteriorInkAlpha = 0.0;
                    if (inkDistance > 0.0 && u_backlitStrength > 0.0
                            && u_backlitWidthPx > 0.0) {
                        float keyLight = max(u_os4DirectionalIntensity, 0.0);
                        float fillLight = max(u_os4DirectionalOppositeIntensity, 0.0);
                        float lightEnergy = clamp(
                                (keyLight + fillLight) * 2.0, 0.0, 1.0);
                        {
                            vec2 edgeNormal = normalize(
                                    os4SdfGradient + vec2(0.00001));
                            float angularIncidence = abs(
                                    dot(edgeNormal, u_invariantLightXY));
                            // Reach controls the dark COLOR domain along the
                            // curved SDF normal, not whether the stroke exists.
                            // At 0% the config disables the rim entirely.
                            float grazing = clamp(1.0 - angularIncidence, 0.0, 1.0);
                            // Delay the darkening until the tangent is close to
                            // the unlit straight edge. A single smoothstep avoids
                            // the previous double-knee and near-instant black arc.
                            // Threshold/span are computed once per material on CPU.
                            rimDarkWeight = smoothstep(
                                    u_backlitGrazingStart,
                                    u_backlitGrazingStart + u_backlitGrazingSpan,
                                    grazing);
                            float lineWidthPx = u_backlitWidthPx;
                            float outsideAa = max(edgeAa * 0.5, 0.25);
                            float entry = smoothstep(0.0, outsideAa,
                                    inkDistance);
                            float exit = 1.0 - smoothstep(
                                    max(lineWidthPx - outsideAa, 0.0),
                                    lineWidthPx + outsideAa, inkDistance);
                            exteriorInkAlpha = clamp(
                                    u_backlitStrength * mix(0.55, 1.0, lightEnergy)
                                    * entry * exit, 0.0, 0.98);
                        }
                    }
                    // The rim uses one identically displaced prepared backdrop
                    // sample on both sides of the SDF zero-crossing. Interior
                    // glass then blends smoothly into Prismal's full lens/OS4
                    // result. Only fragments with visible ink pay this fetch;
                    // the shadow-only path stays texture-sample free.
                    vec3 edgeTransmission = vec3(0.0);
                    if (exteriorInkAlpha > 0.001) {
                        vec2 n = normalize(os4SdfGradient + vec2(0.00001));
                        vec2 rimUv = backdropUv(v_screenTexCoord,
                                -n * max(u_os4ReflectOffsetPx, 1.0)
                                * 0.5 / u_resolution, 0.0);
                        edgeTransmission = u_useBlurredTexture == 1
                                ? texture2D(u_blurredTexture, rimUv).rgb
                                : texture2D(u_backgroundTexture, rimUv).rgb;
                    }

                    // Cast shadow remains below the glass and the optical line.
                    float underlayAlpha = exteriorInkAlpha
                            + exteriorShadowAlpha * (1.0 - exteriorInkAlpha);
                    if (opacity < 0.001) {
                        if (underlayAlpha < 0.001) discard;
                        vec3 rim = exteriorInkAlpha > 0.001
                                ? refractedContourColor(edgeTransmission,
                                        OS4_OPTICAL_RIM_COLOR, rimDarkWeight)
                                : vec3(0.0);
                        vec3 underlayRgb = (rim * exteriorInkAlpha
                                + u_castShadowColor.rgb * exteriorShadowAlpha
                                * (1.0 - exteriorInkAlpha))
                                / max(underlayAlpha, 0.0001);
                        gl_FragColor = vec4(underlayRgb, underlayAlpha);
                        return;
                    }
                    """)
                .replace(FINAL, """
                    // Prismal's 'color' includes full lens refraction,
                    // chromatic dispersion, reflected backdrop and OS4 light.
                    // Blend from the common edge sample into that shading
                    // with the original silhouette alpha.
                    if (underlayAlpha > 0.001) {
                        // Both exterior and interior share edgeTransmission.
                        // Use glass coverage to hand the edge smoothly to the
                        // full Prismal result without a color seam at discard.
                        vec3 rimBase = mix(edgeTransmission, color,
                                smoothstep(0.0, 0.45, opacity));
                        vec3 rim = refractedContourColor(
                                rimBase, OS4_OPTICAL_RIM_COLOR, rimDarkWeight);
                        float innerFringe = smoothstep(
                                -max(u_backlitWidthPx, 0.5), 0.0, surfaceDistance);
                        vec3 contourGlass = mix(
                                color, rim, exteriorInkAlpha * innerFringe);
                        float glassAlpha = opacity * u_transmittance;
                        float combinedAlpha = glassAlpha
                                + underlayAlpha * (1.0 - glassAlpha);
                        vec3 underlayRgb = (rim * exteriorInkAlpha
                                + u_castShadowColor.rgb * exteriorShadowAlpha
                                * (1.0 - exteriorInkAlpha))
                                / max(underlayAlpha, 0.0001);
                        vec3 combinedRgb = (contourGlass * glassAlpha
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
