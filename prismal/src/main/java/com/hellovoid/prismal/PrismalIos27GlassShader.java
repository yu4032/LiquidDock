package com.hellovoid.prismal;

/**
 * One optical contour for every Prismal-rendered LiquidDock surface.
 *
 * <p>Based on the WebGL iOS27 fitting experiment, and applied after the existing
 * SingleEdge, OS4 optical reflection, component gating and invariant hoisting.
 * Reuses the same rounded-rectangle SDF/derivative normal instead of drawing an
 * independent outline. Only the existing two-pixel raster guard extends outside
 * the silhouette. The normal glass/blur/backdrop and native shadow pipelines are
 * unchanged. Small glass scales its edge and meniscus to avoid oversized bands.
 */
final class PrismalIos27GlassShader {
    private PrismalIos27GlassShader() {}

    static String apply(String fragment) {
        if (fragment == null) throw new IllegalArgumentException("fragment == null");
        String source = replace(fragment,
                "if (opacity < 0.001) discard;",
                // The normal SDF coverage still guards the real glass silhouette.
                // Outside translucent ink uses the *same* SDF, within raster overscan.
                "if (opacity < 0.001 && distMask > 1.7) discard;",
                "outer raster guard");

        source = replace(source,
                "vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;",
                """
                // Extend WebGL-calibrated asymmetric meniscus to all glass geometries.
                // Rounded boxes retain the upstream single monotonic edge lens.
                vec2 iosLensNormal = normalize(os4SdfGradient + vec2(1e-5));
                float iosLensSize = smoothstep(22.0, 72.0, minDim);
                dLens *= mix(1.0, mix(0.93, 1.25,
                        smoothstep(-0.85, 0.85, iosLensNormal.y)), iosLensSize);
                vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;
                """, "shared single-edge lens");
        return replace(source, "gl_FragColor = vec4(color, opacity * u_transmittance);",
                """
                float iosAlpha = opacity * u_transmittance;
                float iosSize = clamp(min(u_glassSize.x, u_glassSize.y) / 140.0, 0.58, 1.12);

                // All components use identical SDF edge physics, scaled by thickness.
                // Avoid extra pow/exp/specular work on their large interior pixels.
                if (edgeDist < 12.0 * iosSize && distMask < 1.7) {
                    vec2 iosNormal = normalize(os4SdfGradient + vec2(1e-5));
                    float iosFacing = dot(iosNormal, u_invariantLightXY);
                    float iosMain = pow(max(iosFacing, 0.0), 2.2);
                    float iosOpposite = pow(max(-iosFacing, 0.0), 2.0);
                    float iosHairline = exp(-pow(
                            (edgeDist - 1.14 * iosSize) / (1.22 * iosSize), 2.0) * 1.5);
                    float iosSheen = exp(-max(edgeDist, 0.0) / (7.8 * iosSize));
                    float iosInside = smoothstep(-0.2, 0.7 * iosSize, edgeDist);

                    // Light travels along the entire rounded contour, including corners.
                    float iosPrimaryLight = (0.24 * iosHairline + 0.060 * iosSheen)
                            * iosMain;
                    float iosSecondaryLight = (0.15 * iosHairline + 0.036 * iosSheen)
                            * iosOpposite;
                    float iosCorner = pow(max(abs(iosNormal.x * iosNormal.y) * 2.0, 0.0), 3.5);
                    float iosCornerReflection = 0.055 * iosCorner * iosHairline
                            * (0.45 + 0.55 * max(iosMain, iosOpposite));
                    color += iosInside * vec3(1.0, 0.988, 0.961) * (
                            iosPrimaryLight + iosSecondaryLight + iosCornerReflection);

                    // Internal lateral absorption does not paint over the outer hairline.
                    float iosSide = pow(abs(iosNormal.x), 1.75);
                    float iosAbsorb = iosSide * (0.024 + 0.040 * iosOpposite)
                            * exp(-max(edgeDist, 0.0) / (5.8 * iosSize));
                    color *= 1.0 - iosInside * iosAbsorb;

                    // An exterior dark stroke, derived from *opposite* lighting.
                    // No one-sided gap where the white edge meets the dark contour.
                    float iosOuterRamp = smoothstep(
                            -0.10 * iosSize, 0.40 * iosSize, distMask);
                    float iosOuterTail = 1.0 - smoothstep(
                            0.67 * iosSize, 1.50 * iosSize, distMask);
                    float iosDarkSide = 0.10 + 0.90 * iosSide * iosOpposite;
                    float iosInkAlpha = 0.36 * iosOuterRamp * iosOuterTail
                            * iosDarkSide * u_transmittance;
                    float iosCombined = iosAlpha + iosInkAlpha * (1.0 - iosAlpha);
                    vec3 iosInk = mix(vec3(0.080, 0.063, 0.047),
                            color * 0.27, 0.24);
                    color = (color * iosAlpha
                            + iosInk * iosInkAlpha * (1.0 - iosAlpha))
                            / max(iosCombined, 1e-5);
                    iosAlpha = iosCombined;
                }
                gl_FragColor = vec4(color, iosAlpha);
                """, "shared edge light and outer contour");
    }

    private static String replace(String source, String oldText, String next, String label) {
        int start = source.indexOf(oldText);
        if (start < 0 || source.indexOf(oldText, start + oldText.length()) >= 0) {
            throw new IllegalStateException("iOS27 shared glass " + label + " contract changed");
        }
        return source.substring(0, start) + next + source.substring(start + oldText.length());
    }
}
