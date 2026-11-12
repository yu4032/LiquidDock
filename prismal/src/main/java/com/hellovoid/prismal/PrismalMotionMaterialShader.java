package com.hellovoid.prismal;

/** Preserve the reconstructed edge footprint with three-tap dispersion during motion. */
final class PrismalMotionMaterialShader {
    private static final String MAIN = "void main() {";
    private static final String CHROMA_START = "        float chromaFar = avgDim * 0.5;";
    private static final String CHROMA_END =
            "\n    }\n\n    color = applyVibrancy(color, u_vibrancy);";
    private static final String INNER_FACE = "vec3 ringShadedGlass = color;";

    private PrismalMotionMaterialShader() {}

    static String apply(String upstream) {
        requireOnce(upstream, MAIN);
        requireOnce(upstream, INNER_FACE);
        String fragment = upstream.replace(MAIN, """
                vec3 motion27Grade(vec3 rgb, vec3 gain, vec3 bias) {
                    return clamp(rgb * gain + bias, vec3(0.0), vec3(1.0));
                }

                vec3 motion27BlurFill(vec3 face, vec3 fill,
                        float lighten, float darken, float normal) {
                    float l = clamp(lighten, 0.0, 1.0);
                    float d = clamp(darken, 0.0, 1.0 - l);
                    float base = 1.0 - l - d;
                    vec3 mixed = l * max(face, fill)
                            + d * min(face, fill) + base * face;
                    return mix(mixed, fill, clamp(normal, 0.0, 1.0));
                }

                float motion27BlurRamp(float depth, float reach) {
                    float r = max(reach, 0.01);
                    float a = smoothstep(0.0, r * 0.30, depth);
                    float b = smoothstep(r * 0.30, r * 0.65, depth);
                    float c = smoothstep(r * 0.65, r, depth);
                    return clamp(1.0 - 0.43 * a - 0.33 * b - 0.24 * c,
                            0.0, 1.0);
                }

                """ + MAIN);

        int chromaStart = fragment.indexOf(CHROMA_START);
        int chromaEnd = fragment.indexOf(CHROMA_END, chromaStart);
        if (chromaStart < 0 || chromaEnd <= chromaStart) {
            throw new IllegalStateException("Prismal motion chroma contract changed");
        }
        fragment = fragment.substring(0, chromaStart) + """
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
                        float normalLength = length(os4SdfGradient);
                        if (chromaPx > 0.02 && normalLength > 0.05) {
                            vec2 surfaceNormal = os4SdfGradient / normalLength;
                            vec2 chromaPush = surfaceNormal
                                    * chromaPx / u_resolution;
                            vec2 uvR = backdropUv(v_screenTexCoord,
                                    baseOffset + chromaPush * u_dispersionR, pinchMix);
                            vec2 uvG = uvCenter;
                            vec2 uvB = backdropUv(v_screenTexCoord,
                                    baseOffset - chromaPush * u_dispersionB, pinchMix);
                            if (u_useBlurredTexture == 1) {
                                float r = texture2D(u_blurredTexture, uvR).r;
                                float g = texture2D(u_blurredTexture, uvG).g;
                                float b = texture2D(u_blurredTexture, uvB).b;
                                color = vec3(r, g, b);
                            } else {
                                float r = texture2D(u_backgroundTexture, uvR).r;
                                float g = texture2D(u_backgroundTexture, uvG).g;
                                float b = texture2D(u_backgroundTexture, uvB).b;
                                color = vec3(r, g, b);
                            }
                        } else if (u_useBlurredTexture == 1) {
                            color = texture2D(u_blurredTexture, uvCenter).rgb;
                        } else {
                            color = texture2D(u_backgroundTexture, uvCenter).rgb;
                        }
                """ + fragment.substring(chromaEnd);

        fragment = fragment.replace(INNER_FACE, """
                    vec3 ringShadedGlass = color;
                    if (u_backlitStrength > 0.0 && surfaceDistance < 0.0) {
                        vec3 faceGain = mix(vec3(1.0),
                                clamp(u_glassColor.rgb, vec3(0.55), vec3(1.40)),
                                0.045 * u_backlitStrength);
                        ringShadedGlass = motion27Grade(ringShadedGlass,
                                faceGain, vec3(0.008 * u_backlitStrength));

                        if (u_useBlurredTexture == 1) {
                            float fillReach = clamp(
                                    u_heightTransitionWidth * 2.0, 8.0, 20.0);
                            float blurBlend = motion27BlurRamp(
                                    max(-surfaceDistance, 0.0), fillReach);
                            if (blurBlend > 0.001) {
                                vec3 fillSample = texture2D(
                                        u_blurredTexture, uvCenter).rgb;
                                vec3 gradedFill = motion27Grade(fillSample,
                                        faceGain, vec3(0.008 * u_backlitStrength));
                                vec3 mixedFill = motion27BlurFill(
                                        ringShadedGlass, gradedFill,
                                        0.17 * u_backlitStrength,
                                        0.12 * u_backlitStrength,
                                        0.20 * u_backlitStrength);
                                ringShadedGlass = mix(ringShadedGlass, mixedFill,
                                        0.45 * blurBlend * u_backlitStrength);
                            }
                        }

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
        if (!fragment.contains("vec3 mixedFill = motion27BlurFill(")
                || !fragment.contains("float blurBlend = motion27BlurRamp(")
                || !fragment.contains("float chromaReachPx = min(refractionHeight,")) {
            throw new IllegalStateException("Prismal motion material transform incomplete");
        }
        return fragment;
    }

    private static void requireOnce(String shader, String token) {
        if (shader == null) throw new IllegalArgumentException("shader == null");
        int first = shader.indexOf(token);
        if (first < 0 || shader.indexOf(token, first + token.length()) >= 0) {
            throw new IllegalStateException("Prismal motion shader contract changed: " + token);
        }
    }
}
