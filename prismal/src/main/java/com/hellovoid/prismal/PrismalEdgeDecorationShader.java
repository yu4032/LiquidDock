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

                // A glancing surface reflects the light rather than just
                // showing a translucent copy of the wallpaper. Retain
                // refracted hue and avoid fixed black/white ink endpoints.
                vec3 refractedContourColor(vec3 transmitted, vec3 highlight,
                        float darkWeight, float lightEnergy) {
                    float transmission = mix(1.0, 0.50, darkWeight);
                    float reflectance = (0.06 + 0.60 * lightEnergy)
                            * (1.0 - darkWeight);
                    return clamp(mix(transmitted * transmission, highlight,
                            reflectance), vec3(0.0), vec3(1.0));
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
                    // Share one signed SDF normal for the outer rim and
                    // a thin inner glint. abs(dot) mirrored the dark lobe
                    // onto the lit side and made corners switch abruptly.
                    float rimLightEnergy = 0.0;
                    float rimDarkWeight = 0.0;
                    float exteriorInkAlpha = 0.0;
                    float innerGlint = 0.0;
                    float innerGlintWidthPx = max(edgeAa * 1.75, 1.5);
                    // Extent 0 must disable the rim even for shadow-only nodes.
                    if (u_backlitStrength > 0.0 && u_backlitWidthPx > 0.0
                            && u_backlitGrazingStart < 0.99999
                            && inkDistance > -innerGlintWidthPx
                            && inkDistance < u_backlitWidthPx + edgeAa) {
                        vec2 edgeNormal = normalize(
                                os4SdfGradient + vec2(0.00001));
                        float signedFacing = dot(edgeNormal, u_invariantLightXY);
                        rimLightEnergy = clamp(
                                max(u_os4DirectionalIntensity, 0.0) * 1.9
                                    * smoothstep(-0.20, 0.75, signedFacing)
                                + max(u_os4DirectionalOppositeIntensity, 0.0) * 0.65
                                    * smoothstep(-0.20, 0.75, -signedFacing),
                                0.0, 1.0);
                        // Softness spans the tangent onto the unlit side,
                        // instead of switching in a five-degree corner arc.
                        float grazing = clamp(1.0 - signedFacing, 0.0, 2.0);
                        rimDarkWeight = smoothstep(
                                u_backlitGrazingStart,
                                u_backlitGrazingStart + u_backlitGrazingSpan,
                                grazing);
                        if (inkDistance > 0.0) {
                            float outsideAa = max(edgeAa * 0.5, 0.25);
                            // Center the leading AA ramp on the silhouette.
                            // A 0..AA ramp left a translucent notch at the top.
                            float entry = smoothstep(-outsideAa, outsideAa,
                                    inkDistance);
                            float exit = 1.0 - smoothstep(
                                    max(u_backlitWidthPx - outsideAa, 0.0),
                                    u_backlitWidthPx + outsideAa, inkDistance);
                            exteriorInkAlpha = clamp(
                                    u_backlitStrength * entry * exit, 0.0, 0.98);
                        } else if (surfaceDistance <= 0.0) {
                            // Inner glint uses original glass coverage:
                            // no extra texture tap, pass or geometry.
                            innerGlint = clamp(u_backlitStrength * rimLightEnergy
                                    * smoothstep(-innerGlintWidthPx, -edgeAa * 0.5,
                                            inkDistance)
                                    * (1.0 - smoothstep(-edgeAa * 0.5, 0.0,
                                            inkDistance)) * 0.30, 0.0, 0.30);
                        }
                    }
                    // The SDF is already native resolution, but the Gaussian
                    // color texture is half the physical capture resolution.
                    // A hairline must sample the unblurred source instead.
                    // Both sides reuse the same displaced source sample;
                    // the glass body still uses its normal blur and OS4 optics.
                    // Shadow-only pixels incur no additional texture fetch.
                    vec3 edgeTransmission = vec3(0.0);
                    if (exteriorInkAlpha > 0.001) {
                        vec2 n = normalize(os4SdfGradient + vec2(0.00001));
                        // Mirror OS4's default reflect offset instead of
                        // treating the unset 0px override as a 1px request.
                        // The transmitted rim bends toward the lens interior;
                        // reflection in OS4 bends in the opposite direction.
                        // Keep the displacement bounded on tiny icon glass.
                        float rimReflectPx = u_os4ReflectOffsetPx > 0.0
                                ? u_os4ReflectOffsetPx
                                : clamp(u_glassThickness * 0.38, 4.0, 14.0);
                        float rimBendPx = min(12.0,
                                u_lensRefractionPx * 0.22 + rimReflectPx * 0.45);
                        vec2 rimUv = backdropUv(v_screenTexCoord,
                                -n * rimBendPx / u_resolution, 0.0);
                        edgeTransmission = texture2D(u_backgroundTexture, rimUv).rgb;
                    }

                    // Cast shadow remains below the glass and the optical line.
                    float underlayAlpha = exteriorInkAlpha
                            + exteriorShadowAlpha * (1.0 - exteriorInkAlpha);
                    if (opacity < 0.001) {
                        if (underlayAlpha < 0.001) discard;
                        vec3 rim = exteriorInkAlpha > 0.001
                                ? refractedContourColor(edgeTransmission,
                                        OS4_OPTICAL_RIM_COLOR, rimDarkWeight, rimLightEnergy)
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
                    // Inner glint uses glass coverage, not the translucent
                    // exterior underlay, so the top rim reads as reflection.
                    if (innerGlint > 0.001) {
                        color = mix(color, OS4_OPTICAL_RIM_COLOR, innerGlint);
                    }
                    // Blend from the common edge sample into that shading
                    // with the original silhouette alpha.
                    if (underlayAlpha > 0.001) {
                        // Preserve source detail in the AA band. Hand over
                        // continuously to Prismal's blurred interior only
                        // when the glass coverage is nearly opaque; alpha/SDF
                        // geometry remains unchanged.
                        vec3 rimBase = mix(edgeTransmission, color,
                                smoothstep(0.55, 1.0, opacity));
                        vec3 rim = refractedContourColor(
                                rimBase, OS4_OPTICAL_RIM_COLOR, rimDarkWeight, rimLightEnergy);
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
