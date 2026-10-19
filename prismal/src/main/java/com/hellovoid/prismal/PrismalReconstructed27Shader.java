package com.hellovoid.prismal;

/**
 * Experimental GLES2 adaptation of the macOS 27 QuartzCore reconstruction by
 * medfa12/liquid-glass-react-native (MIT). The reference uses a squircle SDF,
 * mip-map textureLod, separate face/bleed/shadow color matrices and a 63-field
 * uniform block. LiquidDock must retain Launcher geometry, the existing two
 * prepared backdrop textures, and the native zero-copy rendering pipeline.
 *
 * This optional (not baseline) program maps the remaining stages onto those
 * existing inputs, with a compile-time fallback to the smaller optical shader.
 */
final class PrismalReconstructed27Shader {
    private static final String MAIN = "void main() {";
    private static final String CHROMA_START =
            "        float chromaFar = avgDim * 0.5;";
    private static final String CHROMA_END =
            "\n    }\n\n    color = applyVibrancy(color, u_vibrancy);";
    private static final String INNER_FACE =
            "vec3 ringShadedGlass = color;";

    private PrismalReconstructed27Shader() {}

    static String apply(String upstream) {
        requireOnce(upstream, MAIN);
        requireOnce(upstream, CHROMA_START);
        requireOnce(upstream, CHROMA_END);
        requireOnce(upstream, INNER_FACE);
        // Distinct source-over layers remain owned by the optional rim shader.
        requireOnce(upstream, "float outlineAlpha = 0.0;");
        requireOnce(upstream, "float innerHighlightAlpha = 0.0;");

        String fragment = upstream.replace(MAIN, """
                // Adapted from macOS27 seven-sample channel weighting. GLSL ES
                // 1.00 has no textureLod or dynamic sampler-array indexing.
                // Reuse the two prepared Prismal source/blur samplers instead.
                vec3 optical27SevenTap(vec2 offset, vec2 push, float pinch) {
                    vec3 accum = vec3(0.0);
                    for (int i = 0; i < 3; ++i) {
                        float w = 1.0 - float(i) / 3.0;
                        vec2 uv = backdropUv(v_screenTexCoord,
                                offset + push * w * u_dispersionR, pinch);
                        vec3 c;
                        if (u_useBlurredTexture == 1) {
                            c = texture2D(u_blurredTexture, uv).rgb;
                        } else {
                            c = texture2D(u_backgroundTexture, uv).rgb;
                        }
                        accum.r += c.r * w;
                        accum.g += c.g * (1.0 - w);
                    }
                    for (int j = 0; j < 4; ++j) {
                        float w = float(j) / 3.0;
                        vec2 uv = backdropUv(v_screenTexCoord,
                                offset - push * w * u_dispersionB, pinch);
                        vec3 c;
                        if (u_useBlurredTexture == 1) {
                            c = texture2D(u_blurredTexture, uv).rgb;
                        } else {
                            c = texture2D(u_backgroundTexture, uv).rgb;
                        }
                        accum.g += c.g * (1.0 - w);
                        accum.b += c.b * w;
                    }
                    return accum * vec3(0.5, 1.0 / 3.0, 0.5);
                }

                // Explicit affine color transform, mapped to the user's
                // existing tint/OS4 controls instead of adding an incompatible
                // macOS27 63-uniform UBO to GLES2.
                vec3 optical27Grade(vec3 rgb, vec3 gain, vec3 bias) {
                    return clamp(rgb * gain + bias, vec3(0.0), vec3(1.0));
                }

                // The original three-mode partition of unity:
                // base + lighten(max) + darken(min), followed by normal fill.
                vec3 optical27BlurFill(vec3 face, vec3 fill,
                        float lighten, float darken, float normal) {
                    float l = clamp(lighten, 0.0, 1.0);
                    float d = clamp(darken, 0.0, 1.0 - l);
                    float base = 1.0 - l - d;
                    vec3 mixed = l * max(face, fill)
                            + d * min(face, fill) + base * face;
                    return mix(mixed, fill, clamp(normal, 0.0, 1.0));
                }

                // Four monotone distance knots approximate macOS27's LOD
                // blur ramp using existing SHARP + Gaussian textures only.
                // Native ES2 cannot sample the reference textureLod mip chain.
                float optical27BlurRamp(float depth, float reach) {
                    float r = max(reach, 0.01);
                    float a = smoothstep(0.0, r * 0.30, depth);
                    float b = smoothstep(r * 0.30, r * 0.65, depth);
                    float c = smoothstep(r * 0.65, r, depth);
                    return clamp(1.0 - 0.43 * a - 0.33 * b - 0.24 * c,
                            0.0, 1.0);
                }

                """ + MAIN);
        int start = fragment.indexOf(CHROMA_START);
        int end = fragment.indexOf(CHROMA_END, start);
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Prismal chromatic stage changed");
        }
        // Replaces the 3-tap path, not an additional 7 taps on top of it.
        fragment = fragment.substring(0, start) + """
                        // Fix the experimental macOS27 port's UV/px mismatch:
                        // the earlier code treated chromaBase as a UV offset
                        // (growing with output resolution) rather than a
                        // physical logical-pixel displacement. Keep the
                        // chromatic band confined to the actual lens shoulder:
                        // nearest-side SDF normals are piecewise across the
                        // interior medial axis and cause diagonal wedges when
                        // used over most of a large folder or Dock.
                        float chromaReachPx = min(refractionHeight,
                                clamp(minDim * 0.12, 4.0, 14.0));
                        float chromaDepthPx = max(edgeDist, 0.0);
                        float edgeFac = 1.0 - smoothstep(
                                0.0, chromaReachPx, chromaDepthPx);
                        float maxChannelDispersion = max(
                                1.0, max(abs(u_dispersionR), abs(u_dispersionB)));
                        float chromaCapPx = clamp(minDim * 0.018, 0.75, 2.5);
                        float chromaRawPx = caAmt * 0.0018 * minDim * pxNorm;
                        float chromaPx = min(chromaRawPx,
                                chromaCapPx / maxChannelDispersion) * edgeFac;
                        // With an undefined SDF gradient, do not invent a
                        // diagonal normal by normalizing an epsilon vector.
                        float normalLength = length(os4SdfGradient);
                        if (chromaPx > 0.02 && normalLength > 0.05) {
                            vec2 surfaceNormal =
                                    os4SdfGradient / normalLength;
                            vec2 chromaPush = surfaceNormal
                                    * chromaPx / u_resolution;
                            color = optical27SevenTap(baseOffset,
                                    chromaPush, pinchMix);
                        } else if (u_useBlurredTexture == 1) {
                            color = texture2D(u_blurredTexture, uvCenter).rgb;
                        } else {
                            color = texture2D(u_backgroundTexture, uvCenter).rgb;
                        }
                """ + fragment.substring(end);

        fragment = fragment.replace(INNER_FACE, """
                    vec3 ringShadedGlass = color;
                    // macOS27 face/bleed/shadow color matrices are mapped to
                    // the current material tint and native shadow colors.
                    // Do not replace OS4 highlights with a second painted rim.
                    if (u_backlitStrength > 0.0 && surfaceDistance < 0.0) {
                        vec3 faceGain = mix(vec3(1.0),
                                clamp(u_glassColor.rgb, vec3(0.55), vec3(1.40)),
                                0.045 * u_backlitStrength);
                        ringShadedGlass = optical27Grade(ringShadedGlass,
                                faceGain, vec3(0.008 * u_backlitStrength));

                        // macOS27 blur fill: lighten/darken/base partition and
                        // independent normal fill. Constrain additional Gaussian
                        // tap to the rim so full-screen Mali cost stays bounded.
                        if (u_useBlurredTexture == 1) {
                            float fillReach = clamp(
                                    u_heightTransitionWidth * 2.0, 8.0, 20.0);
                            float blurBlend = optical27BlurRamp(
                                    max(-surfaceDistance, 0.0), fillReach);
                            if (blurBlend > 0.001) {
                                vec3 fillSample = texture2D(
                                        u_blurredTexture, uvCenter).rgb;
                                vec3 gradedFill = optical27Grade(fillSample,
                                        faceGain, vec3(0.008 * u_backlitStrength));
                                vec3 mixedFill = optical27BlurFill(
                                        ringShadedGlass, gradedFill,
                                        0.17 * u_backlitStrength,
                                        0.12 * u_backlitStrength,
                                        0.20 * u_backlitStrength);
                                ringShadedGlass = mix(ringShadedGlass, mixedFill,
                                        0.45 * blurBlend * u_backlitStrength);
                            }
                        }

                        // Signed opposing key/fill, bounded to the curved
                        // interior shoulder. Optical highlight alpha stays
                        // independent of external outline alpha.
                        float fillDepth = max(-surfaceDistance, 0.0);
                        float keyMask = 1.0 - smoothstep(0.0,
                                max(3.0, u_backlitWidthPx * 4.0), fillDepth);
                        if (keyMask > 0.001) {
                            vec2 keyNormal = normalize(
                                    os4SdfGradient + vec2(0.00001));
                            float keyFacing = dot(
                                    keyNormal, u_invariantLightXY);
                            float keyFill = opticalOpposingFill(
                                    keyFacing, u_os4DirectionalIntensity,
                                    u_os4DirectionalOppositeIntensity);
                            float softKnee = keyFill /
                                    (1.0 + 0.40 * (1.0 - keyFill));
                            ringShadedGlass += vec3(
                                    0.10 * keyMask * softKnee
                                    * u_backlitStrength);
                            ringShadedGlass = clamp(ringShadedGlass,
                                    vec3(0.0), vec3(1.0));
                        }
                    }
                    """);
        // Keep the reconstructed bleed and GPU-shadow color grades separate;
        // reuse the existing Prismal sampling and material ownership.
        fragment = fragment.replace(
                "vec3 bleedTint = mix(edgeTransmission,",
                "vec3 bleedTint = mix(optical27Grade(edgeTransmission,\n"
                + "                                vec3(0.92), vec3(0.015)),");
        fragment = fragment.replace(
                "u_castShadowColor.rgb * exteriorShadowAlpha",
                "optical27Grade(u_castShadowColor.rgb,\n"
                + "                                vec3(0.96), vec3(0.0))"
                + " * exteriorShadowAlpha");
        if (!fragment.contains("vec3 mixedFill = optical27BlurFill(")
                || !fragment.contains("color = optical27SevenTap(")
                || !fragment.contains("optical27Grade(edgeTransmission,")) {
            throw new IllegalStateException("Prismal 27 shader transform incomplete");
        }
        return fragment;
    }

    private static void requireOnce(String shader, String token) {
        if (shader == null) throw new IllegalArgumentException("shader == null");
        int i = shader.indexOf(token);
        if (i < 0 || shader.indexOf(token, i + token.length()) >= 0) {
            throw new IllegalStateException("Prismal 27 shader contract: " + token);
        }
    }
}
