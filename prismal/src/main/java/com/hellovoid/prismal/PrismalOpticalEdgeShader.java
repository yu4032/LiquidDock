package com.hellovoid.prismal;

/** Scales Prismal's physical optical edge bands without drawing a separate outline. */
public final class PrismalOpticalEdgeShader {
    private static final String PRECISION = "precision highp float;";
    private static final String SHAPE_POSITION = "vec2 pPx = v_shapeCoord * u_glassSize;";
    private static final String EDGE_DISTANCE = "float edgeDist = -distMask;";
    private static final String OPACITY =
            "float opacity = 1.0 - smoothstep(-inset * 0.5, 0.0, distMask);";
    private static final String VIBRANCY_NEUTRAL =
            "if (sat <= 1.001) return rgb;";
    private static final String SHADOW_EXTENT =
            "float shadowExt = mix(0.15, 0.60, u_shadowSoftness > 1.0\n"
            + "        ? clamp(u_shadowSoftness / 20.0, 0.0, 1.0)\n"
            + "        : clamp(u_shadowSoftness, 0.0, 1.0));";
    private static final String OUTWARD =
            "vec2 outward = (length(gradLens) > 1e-4) ? normalize(gradLens) : vec2(0.0, 1.0);";
    private static final String BASE_OFFSET =
            "vec2 baseOffset = edgeRefractionUv;";
    private static final String REFLECTION_MIX = "color = mix(color, reflSample, reflW);";
    private static final String PLAIN_HIGHLIGHT_ADD =
            "color += plusHL * vec3(0.99, 0.995, 1.0);";
    // Upstream shows its surface normal before the OS4 SDF normal or directional light exists.
    private static final String UPSTREAM_NORMAL_DEBUG =
            "if (u_showNormals == 1) {\n"
            + "        gl_FragColor = vec4(N * 0.5 + 0.5, opacity);\n"
            + "        return;\n"
            + "    }";
    private static final String FINAL_GLASS_COLOR =
            "gl_FragColor = vec4(color, opacity * u_transmittance);";
    private static final String OS4_NORMAL_DEBUG = """
                if (u_showNormals == 1) {
                    // Prismal surface in the center; the very same SDF normal used by
                    // OS4 background reflection and directional light at the edge.
                    vec3 os4DebugNormal = normalize(mix(N, os4EdgeNormal3, os4EdgeMask));
                    vec3 os4DebugColor = os4DebugNormal * 0.5 + 0.5;
                    // The white overlay is proportional to the final OS4 directional
                    // contribution, not to the disabled legacy Prismal specular.
                    os4DebugColor = mix(os4DebugColor, vec3(1.0), os4SoftLight * 0.7);
                    gl_FragColor = vec4(os4DebugColor, opacity);
                    return;
                }
                """;

    private static final String OS4_EDGE_UNIFORMS = """
            uniform float u_os4EdgeEnabled;
            uniform float u_os4EdgeWidthPx;
            uniform float u_os4ReflectOffsetPx;
            uniform float u_os4ReflectionStrength;
            uniform float u_os4ReflectionLighten;
            uniform float u_os4DirectionalAngleRange;
            uniform float u_os4DirectionalIntensity;
            uniform float u_os4DirectionalOppositeIntensity;
            uniform float u_os4Darker;
            uniform float u_softGlassMode;
            uniform float u_softEdgePower;
            uniform float u_softLightZ;
            uniform float u_softEdgeAmbient;
            uniform float u_softEdgeLightLength;
            uniform sampler2D u_backgroundBlurredTexture;
            uniform float u_softBackgroundBlurMix;
            uniform float u_softDarkerLow;
            uniform float u_softDarkerHigh;
            uniform float u_softBackgroundSaturation;
            uniform float u_softBackgroundBrightness;
            uniform float u_softInnerBottom;
            uniform float u_softInnerWhite;
            uniform float u_softInnerMix;
            uniform float u_softLuminanceAmount;
            uniform vec4 u_softLuminanceCoefficients;
            uniform float u_softColorPower;
            uniform float u_softUnshade;
            uniform float u_softBurn;
            """;

    private static final String OS4_EDGE_HELPERS = """
            float os4EdgeCurve(float t, float power) {
                t = clamp(t, 0.0, 1.0);
                if (t >= 0.85) return 1.0;
                float shoulder = clamp(mix(0.5, 1.0, sqrt(t)), 0.0, 1.0);
                float smoother = shoulder * shoulder * shoulder
                        * (shoulder * (shoulder * 6.0 - 15.0) + 10.0);
                float shaped = clamp((smoother - 0.5) * 2.0, 0.0, 1.0);
                return 1.0 - pow(1.0 - shaped, max(power, 0.01));
            }

            float os4EdgeCurve(float t) {
                return os4EdgeCurve(t, 1.35);
            }

            float os4ProcessedEdgeDistance(float signedDistance, float width, float power) {
                float safeWidth = max(width, 1e-3);
                if (signedDistance <= -safeWidth) return -safeWidth;
                if (signedDistance < 0.0) {
                    return -os4EdgeCurve(-signedDistance / safeWidth, power) * safeWidth;
                }
                return signedDistance;
            }

            float os4NativeAcos(float value) {
                float x = abs(clamp(value, -1.0, 1.0));
                float angle = (((-0.0187225342 * x + 0.07421875) * x
                        - 0.212036133) * x + 1.5703125) * sqrt(max(1.0 - x, 0.0));
                return value < 0.0 ? 3.14159265 - 2.0 * angle : angle;
            }

            float os4EdgeBand(float edgeDist, float width, float aa) {
                float safeAa = max(aa, 1e-3);
                float safeWidth = max(width, safeAa * 1.5);
                float insideDist = max(edgeDist, 0.0);
                float proximity = clamp(1.0 - insideDist / safeWidth, 0.0, 1.0);
                float shaped = os4EdgeCurve(proximity) * proximity;
                float innerAa = 1.0 - smoothstep(
                        max(safeWidth - safeAa, 0.0), safeWidth + safeAa, insideDist);
                float outerAa = smoothstep(-safeAa, 0.0, edgeDist);
                return clamp(shaped * innerAa * outerAa, 0.0, 1.0);
            }
            """;

    private static final String SOFT_GLASS_BRANCH = """
                if (u_softGlassMode > 0.5) {
                    float softWidth = max(u_os4EdgeWidthPx, 0.1);
                    float softEdgeFraction = clamp(edgeDist / softWidth, 0.0, 1.0);
                    // Prismal's analytic SDF has a constant lateral gradient along a straight
                    // side. OS4's sampled SDF normal curves through the whole bevel volume;
                    // taper the lateral component across the same full depth so refraction and
                    // both light responses meet the flat interior continuously.
                    float softInnerEdgeFade = 1.0 - smoothstep(0.0, 1.0, softEdgeFraction);
                    // mi_glass_sdf fixes this curve exponent at 1.0. uShapeEdgePow belongs
                    // to the separate mi_ltc edge-light pass only.
                    float softEdgeCurve = os4EdgeCurve(softEdgeFraction, 1.0);
                    float softHit = step(0.5, clamp(u_pressProgress, 0.0, 1.0));

                    // Keep Prismal's exact per-surface SDF and corner treatment; only replace
                    // the optical shading, not the OS3 shape geometry.
                    vec2 softNormal2 = os4SdfGradient;
                    if (length(softNormal2) < 1e-4) softNormal2 = vec2(0.0);
                    softNormal2 *= softInnerEdgeFade;
                    vec3 softNormal3 = normalize(vec3(softNormal2, 1.0));

                    // mi_glass_sdf refracts only inside shapeEdgePx and uses this thickness ramp.
                    vec3 softRefractDirection = refract(vec3(0.0, 0.0, -1.0), softNormal3,
                            1.0 / max(u_ior + 0.2 * softHit, 1.0));
                    float softThickness = mix((u_glassThickness - softWidth) * 2.0,
                            u_glassThickness * 2.0, softEdgeFraction);
                    vec2 softRefractionPx = softRefractDirection.xy * softThickness;
                    if (softEdgeFraction >= 1.0) softRefractionPx = vec2(0.0);
                    vec2 softRefractionUv = backdropUv(v_screenTexCoord,
                            softRefractionPx / u_resolution, 0.0);
                    vec4 softRefractionSample = u_useBlurredTexture == 1
                            ? texture2D(u_blurredTexture, softRefractionUv)
                            : texture2D(u_backgroundTexture, softRefractionUv);

                    float softDarkLow = min(u_softDarkerLow, u_softDarkerHigh);
                    float softDarkHigh = max(u_softDarkerHigh, softDarkLow + 1e-4);
                    vec3 softPurple = vec3(0.07873535, 0.0284729, 0.09277344);
                    float softBaseLuma = dot(softRefractionSample.rgb,
                            vec3(0.2125244, 0.71484375, 0.07214355));
                    float softDarkAmount = clamp(u_os4Darker, 0.0, 1.0)
                            * smoothstep(softDarkLow, softDarkHigh, softBaseLuma);
                    vec3 softBase = mix(softRefractionSample.rgb, softPurple, softDarkAmount);

                    // uHit affects IOR and inner tint. Map uIconMove's local light to Prismal's
                    // existing press point so the OS3 interaction contract stays intact.
                    vec2 softHitPx = (u_glowCenter - vec2(0.5)) * u_glassSize;
                    float softHitRadius = max(minDim * 1.5, 1.0);
                    float softIconMotion = clamp(u_pressProgress, 0.0, 1.0)
                            * clamp(u_glowStrength, 0.0, 1.0)
                            * (1.0 - smoothstep(softHitRadius * 0.5, softHitRadius,
                                    distance(pPx, softHitPx)));
                    if (softIconMotion > 0.001) {
                        float hitLuma = dot(softBase,
                                vec3(0.2125244, 0.71484375, 0.07214355));
                        float hitLumaMix = smoothstep(0.0, 0.5, hitLuma);
                        float hitGain = 1.0 + smoothstep(0.0, 1.0, softIconMotion)
                                * mix(0.75, 0.4, hitLumaMix);
                        softBase = softBase * hitGain + vec3(
                                mix(1.0 - hitLuma, hitLuma, hitLumaMix) * (hitGain - 1.0));
                    }

                    // Native reflection weight uses edgeCurve; its displacement uses linear depth.
                    vec2 softReflectionPx = softNormal3.xy * (2.0 * softNormal3.z)
                            * u_os4ReflectOffsetPx * (1.0 - softEdgeFraction);
                    vec2 softReflectionUv = backdropUv(v_screenTexCoord,
                            softReflectionPx / u_resolution, 0.0);
                    vec4 softReflectionSample = u_useBlurredTexture == 1
                            ? texture2D(u_blurredTexture, softReflectionUv)
                            : texture2D(u_backgroundTexture, softReflectionUv);
                    float softReflectionLuma = dot(softReflectionSample.rgb,
                            vec3(0.2125244, 0.71484375, 0.07214355));
                    float softReflectionDark = clamp(u_os4Darker, 0.0, 1.0)
                            * smoothstep(softDarkLow, softDarkHigh, softReflectionLuma);
                    vec3 softReflection = mix(softReflectionSample.rgb,
                            softPurple, softReflectionDark);
                    float softMixWeight = (1.0 - softEdgeCurve) * u_os4ReflectionStrength;
                    vec3 softColor = mix(softBase, softReflection, softMixWeight);
                    float softReflectionLift = (1.0 - softEdgeFraction)
                            * max(u_os4ReflectionLighten, 0.0)
                            * (0.55 + 0.45 * smoothstep(0.2, 1.0,
                                    distance(vec3(1.0), softColor)));
                    softColor += softReflection * softReflectionLift;

                    float softColorAlpha = max(mix(softRefractionSample.a,
                            softReflectionSample.a, softMixWeight), 1e-4);
                    float softLuma = clamp(dot(softColor / softColorAlpha,
                            vec3(0.21240234, 0.71484375, 0.07208252)), 0.0, 1.0);
                    vec4 softCurve = u_softLuminanceCoefficients;
                    float softCurveLuma = clamp((((softLuma * softCurve.x + softCurve.y)
                            * softLuma + softCurve.z) * softLuma + softCurve.w), 0.0, 1.0);
                    softColor = mix(softColor, vec3(softCurveLuma * softColorAlpha),
                            clamp(u_softLuminanceAmount, 0.0, 1.0));
                    float softGlassLuma = dot(softColor,
                            vec3(0.21240234, 0.71484375, 0.07208252));
                    softColor = mix(vec3(softGlassLuma), softColor, u_vibrancy)
                            + vec3(u_brightness * softColorAlpha);

                    // OS4 has a separately filtered, un-refracted background-color source.
                    vec2 softBackgroundUv = backdropUv(v_screenTexCoord, vec2(0.0), 0.0);
                    vec4 softBackgroundRaw = texture2D(u_backgroundTexture, softBackgroundUv);
                    vec4 softBackgroundBlur = texture2D(
                            u_backgroundBlurredTexture, softBackgroundUv);
                    vec4 softBackgroundSample = mix(softBackgroundRaw, softBackgroundBlur,
                            clamp(u_softBackgroundBlurMix, 0.0, 1.0));
                    float softBackgroundLuma = dot(softBackgroundSample.rgb,
                            vec3(0.2125244, 0.71484375, 0.07214355));
                    float softBackgroundDark = clamp(u_os4Darker, 0.0, 1.0)
                            * smoothstep(softDarkLow, softDarkHigh, softBackgroundLuma);
                    vec3 softInner = mix(softBackgroundSample.rgb,
                            softPurple, softBackgroundDark);
                    float softInnerLuma = dot(softInner,
                            vec3(0.21240234, 0.71484375, 0.07208252));
                    softInner = mix(vec3(softInnerLuma), softInner,
                            u_softBackgroundSaturation)
                            + vec3(u_softBackgroundBrightness * softBackgroundSample.a);
                    softInner = mix(softInner, vec3(1.0),
                            clamp(u_softInnerWhite, 0.0, 1.0));
                    softInner.g += 0.2 * softHit;
                    softInner.b += 0.05 * softHit;
                    float softBurnLuma = clamp(dot(softInner,
                            vec3(0.2125244, 0.71484375, 0.07214355)), 0.0, 1.0);
                    float softBurnDelta = pow(softBurnLuma, max(u_softBurn, 0.5)) - 0.5;
                    float softBurnMix = mix(softBurnLuma,
                            1.0, 1.58691406 * softBurnDelta * softBurnDelta
                                    * softBurnDelta + 0.5) * 0.79980469;
                    softColor = mix(mix(vec3(1.0), softInner, softBurnMix),
                            u_glassColor.rgb, u_glassColor.a)
                            * mix(softColor, vec3(1.0),
                                    clamp(u_softInnerMix * softBurnMix, 0.0, 1.0));
                    float softBottom = smoothstep(0.0, 1.0, v_shapeCoord.y + 0.5);
                    softColor += vec3(softBottom * softBottom * u_softInnerBottom);

                    // mi_glass_sdf applies its two-sided angle falloff only inside the SDF edge volume.
                    vec3 softLightNormal = normalize(vec3(softNormal3.x,
                            -softNormal3.y, softNormal3.z));
                    vec3 softLightDirection = normalize(vec3(u_lightDir, u_softLightZ));
                    float softMainDot = dot(softLightNormal, softLightDirection);
                    float softOppositeDot = 2.0 * softNormal3.z * softLightDirection.z
                            - softMainDot;
                    float softLightRange = max(u_os4DirectionalAngleRange, 0.01);
                    float softMainAngle = os4NativeAcos(softMainDot);
                    float softOppositeAngle = os4NativeAcos(softOppositeDot);
                    float softDirectional = max(softMainDot, 0.0)
                            * max(u_os4DirectionalIntensity
                                    * (1.0 - softMainAngle / (3.14159265 * softLightRange)), 0.0)
                            + max(softOppositeDot, 0.0)
                            * max(u_os4DirectionalOppositeIntensity
                                    * (1.0 - softOppositeAngle / (3.14159265 * softLightRange)), 0.0);
                    vec2 softDynamic2 = softHitPx - pPx;
                    vec3 softDynamicDirection = normalize(vec3(softDynamic2, 1.0));
                    float softDynamicMainDot = dot(softLightNormal, softDynamicDirection);
                    float softDynamicOppositeDot = 2.0 * softNormal3.z * softDynamicDirection.z
                            - softDynamicMainDot;
                    float softDynamic = max(softDynamicMainDot, 0.0)
                            * max(u_os4DirectionalIntensity
                                    * (1.0 - os4NativeAcos(softDynamicMainDot)
                                            / (3.14159265 * softLightRange)), 0.0)
                            + max(softDynamicOppositeDot, 0.0)
                            * max(u_os4DirectionalOppositeIntensity
                                    * (1.0 - os4NativeAcos(softDynamicOppositeDot)
                                            / (3.14159265 * softLightRange)), 0.0);
                    softDirectional = (softDirectional + softDynamic * softIconMotion)
                            * softInnerEdgeFade;
                    float softDirectionalLuma = dot(softColor,
                            vec3(0.2126, 0.7152, 0.0722));
                    float softLumaMix = smoothstep(0.0, 0.5, softDirectionalLuma);
                    float softColorDistance = smoothstep(0.2, 1.0,
                            distance(vec3(1.0), softColor));
                    float softGain = 1.0 + smoothstep(0.0, 1.0,
                            (softDirectional * 2.0)
                                    * (softDirectionalLuma
                                            * (0.5 + 0.5 * softColorDistance)))
                            * mix(0.75, 0.4, softLumaMix);
                    softColor = softColor * softGain + vec3(
                            mix(1.0 - softDirectionalLuma,
                                    softDirectionalLuma, softLumaMix)
                                    * (softGain - 1.0));

                    // mi_ltc's separate narrow edge-light pass: finite-difference the same
                    // Prismal rounded-box SDF and use its native 2.5px edge curve mask.
                    vec2 softPixelStep = vec2(max(length(dFdx(pPx)), 0.5),
                            max(length(dFdy(pPx)), 0.5));
                    float softSdfXp = os4ProcessedEdgeDistance(
                            sdRoundBox(pPx + vec2(softPixelStep.x, 0.0),
                                    halfSz, crMask, u_sminSmoothing),
                            softWidth, u_softEdgePower);
                    float softSdfXm = os4ProcessedEdgeDistance(
                            sdRoundBox(pPx - vec2(softPixelStep.x, 0.0),
                                    halfSz, crMask, u_sminSmoothing),
                            softWidth, u_softEdgePower);
                    float softSdfYp = os4ProcessedEdgeDistance(
                            sdRoundBox(pPx + vec2(0.0, softPixelStep.y),
                                    halfSz, crMask, u_sminSmoothing),
                            softWidth, u_softEdgePower);
                    float softSdfYm = os4ProcessedEdgeDistance(
                            sdRoundBox(pPx - vec2(0.0, softPixelStep.y),
                                    halfSz, crMask, u_sminSmoothing),
                            softWidth, u_softEdgePower);
                    vec3 softLtcNormal = normalize(vec3(
                            (softSdfXp - softSdfXm) / (2.0 * softPixelStep.x),
                            -(softSdfYp - softSdfYm) / (2.0 * softPixelStep.y), 1.0));
                    softLtcNormal = normalize(vec3(softLtcNormal.xy * softInnerEdgeFade,
                            softLtcNormal.z));
                    vec3 softLtcLightNormal = normalize(vec3(softLtcNormal.x,
                            -softLtcNormal.y, softLtcNormal.z));
                    float softLtcAa = max(length(vec2(dFdx(distMask), dFdy(distMask))), 1e-4);
                    float softLtcCoverage = 1.0
                            - smoothstep(-softLtcAa, softLtcAa, distMask);
                    float softLtcMainDot = dot(softLtcLightNormal, softLightDirection);
                    float softLtcOppositeDot = dot(softLtcLightNormal,
                            vec3(-softLightDirection.x, -softLightDirection.y,
                                    softLightDirection.z));
                    float softLtcCurve = os4EdgeCurve(2.5 / softWidth, u_softEdgePower);
                    float softLtcEdgePosition = clamp(-os4ProcessedEdgeDistance(
                            distMask, softWidth, u_softEdgePower) / softWidth, 0.0, 1.0);
                    float softLtcMask = smoothstep(0.0, max(softLtcCurve, 1e-4),
                            softLtcEdgePosition) * softLtcCoverage * u_transmittance;
                    float softLtcLight = clamp(
                            max(u_os4DirectionalIntensity * (1.0
                                    - acos(clamp(softLtcMainDot, -1.0, 1.0))
                                            / (3.14159265 * softLightRange)), 0.0)
                                    * pow(max(softLtcMainDot, 0.0),
                                            max(u_softEdgeLightLength, 0.01))
                                    + max(u_os4DirectionalOppositeIntensity * (1.0
                                            - acos(clamp(softLtcOppositeDot, -1.0, 1.0))
                                                    / (3.14159265 * softLightRange)), 0.0)
                                    * pow(max(softLtcOppositeDot, 0.0),
                                            max(u_softEdgeLightLength, 0.01))
                                    + u_softEdgeAmbient * clamp(length(softLtcNormal.xy), 0.0, 1.0),
                            0.0, 1.0) * softLtcMask;
                    softColor = mix(softColor, vec3(1.0), clamp(softLtcLight * 0.16, 0.0, 0.24));

                    softColor = pow(max(softColor, vec3(0.0)),
                            vec3(max(u_softColorPower, 0.1)));
                    softColor = mix(clamp(softColor, 0.0, 1.0),
                            u_glassColor.rgb, clamp(u_softUnshade, 0.0, 1.0));
                    float softOpacity = 1.0 - smoothstep(-2.0, 0.0, distMask);
                    gl_FragColor = vec4(softColor, softOpacity * u_transmittance);
                    return;
                }
                """;

    public static String apply(String source) {
        requireSingle(source, PRECISION);
        requireSingle(source, SHAPE_POSITION);
        requireSingle(source, EDGE_DISTANCE);
        requireSingle(source, OPACITY);
        requireSingle(source, OUTWARD);
        requireSingle(source, BASE_OFFSET);
        requireSingle(source, VIBRANCY_NEUTRAL);
        requireSingle(source, SHADOW_EXTENT);
        requireSingle(source, REFLECTION_MIX);
        requireSingle(source, PLAIN_HIGHLIGHT_ADD);
        requireSingle(source, UPSTREAM_NORMAL_DEBUG);
        requireSingle(source, FINAL_GLASS_COLOR);
        requireSingle(source, "if (opacity < 0.001) discard;");
        return source
                .replace(PRECISION,
                        "#extension GL_OES_standard_derivatives : enable\n\n"
                                + PRECISION + "\n\n" + OS4_EDGE_UNIFORMS + "\n"
                                + OS4_EDGE_HELPERS)
                .replace(SHAPE_POSITION, SHAPE_POSITION + "\n"
                        + "    vec2 edgePixelStep = vec2(\n"
                        + "            max(length(dFdx(pPx)), 0.5),\n"
                        + "            max(length(dFdy(pPx)), 0.5));")
                .replace(EDGE_DISTANCE, EDGE_DISTANCE + "\n"
                        + "    float opticalEdgeScale = clamp(u_highlightWidth, 0.5, 3.0);\n"
                        + "    float edgeAa = max(fwidth(distMask), 0.75);\n"
                        + "    // distMask is already available before discard. Reuse the same\n"
                        + "    // derivative quad that fwidth() uses instead of evaluating the\n"
                        + "    // expensive rounded-box SDF four more times per fragment.\n"
                        + "    vec2 os4SdfGradient = vec2(\n"
                        + "            dFdx(distMask) / max(edgePixelStep.x, 1e-3),\n"
                        + "            -dFdy(distMask) / max(edgePixelStep.y, 1e-3));\n"
                        + "    float os4DefaultEdgeWidthPx = clamp(6.0 * opticalEdgeScale, 3.0, 18.0);\n"
                        + "    float os4EdgeWidthPx = u_os4EdgeWidthPx > 0.0\n"
                        + "            ? u_os4EdgeWidthPx : os4DefaultEdgeWidthPx;\n"
                        + "    float os4EdgeT = clamp(edgeDist / max(os4EdgeWidthPx, 1.0), 0.0, 1.0);\n"
                        + "    float os4EdgeMask = os4EdgeBand(edgeDist, os4EdgeWidthPx, edgeAa)\n"
                        + "            * step(0.5, u_os4EdgeEnabled);")
                .replace("if (opacity < 0.001) discard;",
                        "if (opacity < 0.001) discard;\n" + SOFT_GLASS_BRANCH)
                .replace(VIBRANCY_NEUTRAL,
                        "if (abs(sat - 1.0) <= 0.001) return rgb;")
                .replace(SHADOW_EXTENT, """
                        // 0..20 (GUI 0..2000) is continuous; default 1000 (10.0) remains 0.375.
                        float shadowExt = mix(0.15, 0.60, clamp(u_shadowSoftness / 20.0, 0.0, 1.0));
                        """)
                .replace(OUTWARD, OUTWARD + "\n"
                        + "    vec3 os4EdgeNormal3 = normalize(vec3(os4SdfGradient, 1.0));")
                .replace(REFLECTION_MIX,
                        "color = mix(color, reflSample, reflW * (1.0 - step(0.5, u_softGlassMode)));\n"
                        + "    float os4DefaultReflectOffsetPx = clamp(u_glassThickness * 0.38, 4.0, 14.0);\n"
                        + "    float os4ReflectOffsetPx = u_os4ReflectOffsetPx > 0.0\n"
                        + "            ? u_os4ReflectOffsetPx : os4DefaultReflectOffsetPx;\n"
                        + "    vec2 os4ReflectUvOffset = os4EdgeNormal3.xy\n"
                        + "            * (2.0 * os4EdgeNormal3.z)\n"
                        + "            * os4ReflectOffsetPx * (1.0 - os4EdgeT) / u_resolution\n"
                        + "            * os4EdgeMask;\n"
                        + "    vec2 os4ReflectUv = clamp(uvCenter + os4ReflectUvOffset,\n"
                        + "            vec2(0.0), vec2(1.0));\n"
                        + "    float os4ReflectionWeight = clamp(os4EdgeMask\n"
                        + "            * max(u_os4ReflectionStrength, 0.0)\n"
                        + "            * (1.0 - step(0.5, u_softGlassMode)), 0.0, 1.0);\n"
                        + "    // The 0.5x Gaussian is for the body, not the optical meniscus.\n"
                        + "    // One sharp prepared-source tap preserves background displacement\n"
                        + "    // across the narrow native-resolution edge band.\n"
                        + "    if (os4ReflectionWeight > 0.001) {\n"
                        + "        vec3 os4EdgeReflection = texture2D(u_backgroundTexture, os4ReflectUv).rgb;\n"
                        + "        color = mix(color, os4EdgeReflection, os4ReflectionWeight);\n"
                        + "    }")
                .replace(PLAIN_HIGHLIGHT_ADD, PLAIN_HIGHLIGHT_ADD + "\n"
                        + "    vec3 os4LightDir3 = normalize(vec3(Lxy, 1.0));\n"
                        + "    float os4MainFacing = max(dot(os4EdgeNormal3, os4LightDir3), 0.0);\n"
                        + "    float os4OppositeFacing = max(\n"
                        + "            dot(vec3(-os4EdgeNormal3.xy, os4EdgeNormal3.z),\n"
                        + "                    os4LightDir3), 0.0);\n"
                        + "    float os4AngleRange = max(u_os4DirectionalAngleRange, 0.05);\n"
                        + "    float os4MainAngle = acos(clamp(os4MainFacing, 0.0, 1.0));\n"
                        + "    float os4OppositeAngle = acos(clamp(os4OppositeFacing, 0.0, 1.0));\n"
                        + "    float os4MainFalloff = max(1.0\n"
                        + "            - os4MainAngle / (3.14159265 * os4AngleRange), 0.0);\n"
                        + "    float os4OppositeFalloff = max(1.0\n"
                        + "            - os4OppositeAngle / (3.14159265 * os4AngleRange), 0.0);\n"
                        + "    float os4Directional = os4MainFacing\n"
                        + "            * max(u_os4DirectionalIntensity, 0.0) * os4MainFalloff\n"
                        + "            + os4OppositeFacing\n"
                        + "            * max(u_os4DirectionalOppositeIntensity, 0.0)\n"
                        + "            * os4OppositeFalloff;\n"
                        + "    float os4SoftLight = clamp(os4Directional * os4EdgeMask\n"
                        + "            * (1.0 - step(0.5, u_softGlassMode)), 0.0, 1.0);\n"
                        + "    float os4Luma = dot(color, vec3(0.2126, 0.7152, 0.0722));\n"
                        + "    float os4DarkResponse = 1.0 - smoothstep(0.35, 0.92, os4Luma);\n"
                        + "    color += vec3(os4SoftLight\n"
                        + "            * (0.42 + max(u_os4ReflectionLighten, 0.0) * os4DarkResponse));")
                .replace("minDim * 0.09", "minDim * 0.09 * opticalEdgeScale")
                .replace("tw * 0.42", "tw * 0.42 * opticalEdgeScale")
                .replace("minDim * 0.12", "minDim * 0.12 * opticalEdgeScale")
                .replace("bandFracR * rimBandTight",
                        "bandFracR * opticalEdgeScale * rimBandTight")
                // Only the debug path changes: normal glass output is untouched.
                .replace(UPSTREAM_NORMAL_DEBUG,
                        "// OS4 normal debug is deferred until directional lighting is available.")
                .replace(FINAL_GLASS_COLOR, OS4_NORMAL_DEBUG + FINAL_GLASS_COLOR);
    }

    private static void requireSingle(String source, String anchor) {
        int first = source.indexOf(anchor);
        if (first < 0 || source.indexOf(anchor, first + anchor.length()) >= 0) {
            throw new IllegalArgumentException(
                    "Expected exactly one Prismal optical-edge anchor: " + anchor);
        }
    }

    private PrismalOpticalEdgeShader() {}
}
