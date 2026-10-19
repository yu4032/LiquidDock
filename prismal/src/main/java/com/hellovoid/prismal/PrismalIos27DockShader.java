package com.hellovoid.prismal;

/**
 * Dock-only iOS27 WebGL calibration port. A single signed-distance field and
 * pre-discard OS4 normal own refraction, inner highlight and outer dark contour.
 * Native MiShadow still owns the soft drop shadow.
 */
final class PrismalIos27DockShader {
    private PrismalIos27DockShader() {}

    static String apply(String source) {
        if (source == null) throw new IllegalArgumentException("source == null");
        String out = replace(source, "uniform float u_os4EdgeEnabled;",
                "uniform float u_os4EdgeEnabled;\nuniform float u_ios27DockEnabled;", "uniform");
        out = replace(out, "if (opacity < 0.001) discard;",
                "if (opacity < 0.001 && (u_ios27DockEnabled < 0.5 || distMask > 1.7)) discard;",
                "outer contour guard");
        out = replace(out,
                "vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;",
                """
                if (u_ios27DockEnabled > 0.5) {
                    // One edge-normal lens, with slightly stronger lower meniscus.
                    vec2 iosLensNormal = normalize(os4SdfGradient + vec2(1e-5));
                    dLens *= mix(0.92, 1.30, smoothstep(-0.85, 0.85, iosLensNormal.y));
                }
                vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;
                """, "single-edge refraction");
        return replace(out,
                "gl_FragColor = vec4(color, opacity * u_transmittance);",
                """
                float iosAlpha = opacity * u_transmittance;
                if (u_ios27DockEnabled > 0.5) {
                    vec2 iosNormal = normalize(os4SdfGradient + vec2(1e-5));
                    float iosTop = pow(max(-iosNormal.y, 0.0), 2.4);
                    float iosBottom = pow(max(iosNormal.y, 0.0), 2.4);
                    float iosInside = smoothstep(-0.30, 0.65, edgeDist);
                    float iosHairline = exp(-pow((edgeDist - 1.22) / 1.25, 2.0) * 1.6);
                    float iosSheen = exp(-max(edgeDist, 0.0) / 9.2);
                    float iosVariation = 0.74 + 0.26
                            * sqrt(max(0.0, 1.0 - abs(pPx.x) / max(halfSz.x, 1.0)));
                    float iosTopLight = 0.21 * (0.78 * iosHairline + 0.25 * iosSheen)
                            * iosTop * iosVariation;
                    float iosBottomLight = 0.17 * (0.77 * iosHairline + 0.30 * iosSheen)
                            * iosBottom;
                    float iosCornerA = pow(max(dot(iosNormal,
                            normalize(vec2(-0.55, -0.82))), 0.0), 9.0)
                            * exp(-pow((edgeDist - 2.2) / 2.8, 2.0) * 1.6);
                    float iosCornerB = pow(max(dot(iosNormal,
                            normalize(vec2(0.66, 0.75))), 0.0), 9.0)
                            * exp(-pow((edgeDist - 1.7) / 2.4, 2.0) * 1.6);
                    color += iosInside * vec3(1.0, 0.985, 0.946)
                            * (iosTopLight + iosBottomLight
                               + 0.12 * (iosCornerA + iosCornerB));

                    // Inner side absorption never replaces the inner white highlight.
                    float iosSideShade = pow(abs(iosNormal.x), 2.4)
                            * exp(-max(edgeDist, 0.0) / 7.0) * 0.053;
                    color *= 1.0 - iosSideShade * iosInside;

                    // Dark contour exclusively outside the existing silhouette.
                    // No independent Drawable geometry or disjoint black cutout.
                    float iosSide = 0.15 + 0.85 * pow(abs(iosNormal.x), 1.3);
                    float iosOuterRamp = smoothstep(-0.30, 0.45, distMask);
                    float iosOuterTail = 1.0 - smoothstep(0.54, 1.55, distMask);
                    float iosStrokeAlpha = 0.53 * 0.58 * iosOuterRamp * iosOuterTail
                            * iosSide * u_transmittance;
                    float iosCombinedAlpha = iosAlpha + iosStrokeAlpha * (1.0 - iosAlpha);
                    vec3 iosInk = mix(vec3(0.09, 0.068, 0.044),
                            color * 0.29, 0.27);
                    color = mix(color, iosInk,
                            iosStrokeAlpha / max(iosCombinedAlpha, 1e-4));
                    iosAlpha = iosCombinedAlpha;
                }
                gl_FragColor = vec4(color, iosAlpha);
                """, "final optical contour");
    }

    private static String replace(String src, String oldText, String replacement, String part) {
        int idx = src.indexOf(oldText);
        if (idx < 0 || src.indexOf(oldText, idx + oldText.length()) >= 0) {
            throw new IllegalStateException("iOS27 Dock " + part + " shader contract changed");
        }
        return src.substring(0, idx) + replacement + src.substring(idx + oldText.length());
    }
}
