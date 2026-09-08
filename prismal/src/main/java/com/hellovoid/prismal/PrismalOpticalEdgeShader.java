package com.hellovoid.prismal;

/**
 * Adapts Prismal's optical edge to the raster footprint and OS4's volumetric edge model.
 *
 * <p>The OS4 reference separates edge geometry from a thin final outline: signed-distance depth
 * drives material thickness/reflection while a finite-width Bloom stroke is lit from the edge
 * normal. Milestone 1 keeps those mechanics inside Prismal's existing logical-resolution output
 * pass so the optical model can be proven before introducing a dedicated Bloom FBO/cache owner.</p>
 */
public final class PrismalOpticalEdgeShader {
    private static final String PRECISION = "precision highp float;";
    private static final String SHAPE_POSITION = "vec2 pPx = v_shapeCoord * u_glassSize;";
    private static final String EDGE_DISTANCE = "float edgeDist = -distMask;";
    private static final String OPACITY =
            "float opacity = 1.0 - smoothstep(-inset * 0.5, 0.0, distMask);";
    private static final String REFLECTION_SHELL =
            "float reflShell = smoothstep(clamp(minDim * 0.09, 1.8, 18.0), 0.0, edgeDist) * smoothstep(-3.0, 0.0, distMask);";
    private static final String OUTWARD =
            "vec2 outward = (length(gradLens) > 1e-4) ? normalize(gradLens) : vec2(0.0, 1.0);";
    private static final String MENISCUS_BLEND =
            "float menBlend = smoothstep(tw * 0.42, 0.0, edgeDist) * smoothstep(-4.0, 0.0, distMask) * 0.62;";
    private static final String SILHOUETTE_WIDTH =
            "float silW = clamp(minDim * 0.12, 2.5, 34.0);";
    private static final String SILHOUETTE_BAND =
            "float edgeSil = smoothstep(silW, 0.0, edgeDist) * smoothstep(-4.5, 0.0, distMask);";
    private static final String BASE_OFFSET = "vec2 baseOffset = edgeRefractionUv;";
    private static final String REFLECTION_MIX = "color = mix(color, reflSample, reflW);";
    private static final String RIM_BAND_RADIUS =
            "float bandR = clamp(minDim * bandFracR * rimBandTight, mix(0.28, 0.65, 1.0 - smallGlass), min(12.0, minDim * 0.1));";
    private static final String RIM_BAND =
            "float shellRim = smoothstep(bandR, bandR * 0.06, edgeDist) * smoothstep(-2.2, 0.0, distMask);";
    private static final String REFLECTION_DIRECTION =
            "vec2 gDir = normalize(gradLens + vec2(1e-4));";
    private static final String LIGHTING_NORMAL =
            "vec2 gN = normalize(gradLens + vec2(1e-4));";
    private static final String FACE_SHEEN =
            "float faceSheenSoft = smoothstep(bandR * 1.8, bandR * 0.08, edgeDist) * smoothstep(-2.0, 0.0, distMask)";
    private static final String PLAIN_HIGHLIGHT =
            "float plusHL = smoothstep(bandR * 0.95, bandR * 0.05, edgeDist) * u_plainHighlight * u_rimStrength";
    private static final String PLAIN_HIGHLIGHT_ADD =
            "color += plusHL * vec3(0.99, 0.995, 1.0);";

    private static final String OS4_EDGE_HELPERS = """
            float os4EdgeCurve(float t) {
                t = clamp(t, 0.0, 1.0);
                if (t >= 0.85) return 1.0;
                float shoulder = clamp(mix(0.5, 1.0, sqrt(clamp(t, 0.0, 1.0))), 0.0, 1.0);
                float smoother = shoulder * shoulder * shoulder
                        * (shoulder * (shoulder * 6.0 - 15.0) + 10.0);
                float shaped = clamp((smoother - 0.5) * 2.0, 0.0, 1.0);
                return 1.0 - pow(1.0 - shaped, 1.35);
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

    public static String apply(String source) {
        requireSingle(source, PRECISION);
        requireSingle(source, SHAPE_POSITION);
        requireSingle(source, EDGE_DISTANCE);
        requireSingle(source, OPACITY);
        requireSingle(source, REFLECTION_SHELL);
        requireSingle(source, OUTWARD);
        requireSingle(source, MENISCUS_BLEND);
        requireSingle(source, SILHOUETTE_WIDTH);
        requireSingle(source, SILHOUETTE_BAND);
        requireSingle(source, BASE_OFFSET);
        requireSingle(source, REFLECTION_MIX);
        requireSingle(source, RIM_BAND_RADIUS);
        requireSingle(source, RIM_BAND);
        requireSingle(source, REFLECTION_DIRECTION);
        requireSingle(source, LIGHTING_NORMAL);
        requireSingle(source, FACE_SHEEN);
        requireSingle(source, PLAIN_HIGHLIGHT);
        requireSingle(source, PLAIN_HIGHLIGHT_ADD);

        return source
                .replace(PRECISION,
                        "#extension GL_OES_standard_derivatives : enable\n\n"
                                + PRECISION + "\n\n" + OS4_EDGE_HELPERS)
                .replace(SHAPE_POSITION, SHAPE_POSITION + "\n"
                        + "    vec2 edgePixelStep = vec2(\n"
                        + "            max(length(dFdx(pPx)), 0.5),\n"
                        + "            max(length(dFdy(pPx)), 0.5));\n"
                        + "    float edgePixelFootprint = max(edgePixelStep.x, edgePixelStep.y);")
                .replace(EDGE_DISTANCE, EDGE_DISTANCE + "\n"
                        + "    float opticalEdgeScale = clamp(u_highlightWidth, 0.5, 3.0);\n"
                        + "    float edgeAa = max(fwidth(distMask), 0.75);\n"
                        + "    edgeAa = max(edgeAa, edgePixelFootprint * 0.55);\n"
                        + "    // OS4 keeps material thickness separate from the visible Bloom width.\n"
                        + "    float os4EdgePx = clamp(minDim * 0.060, 6.0, 18.0);\n"
                        + "    float os4ThicknessPx = max(u_glassThickness, os4EdgePx + 6.0);\n"
                        + "    float os4ReflectOffsetPx = clamp(os4ThicknessPx * 0.38, 4.0, 14.0);\n"
                        + "    float os4EdgeDepth = clamp(edgeDist / max(os4EdgePx, 1.0), 0.0, 1.0);")
                .replace(REFLECTION_SHELL,
                        "float reflShell = os4EdgeBand(edgeDist, "
                                + "clamp(minDim * 0.09 * opticalEdgeScale, 1.8, 18.0), edgeAa) "
                                + "* smoothstep(-3.0, 0.0, distMask);")
                .replace(OPACITY,
                        "float opacity = 1.0 - smoothstep(-edgeAa, edgeAa, distMask);")
                .replace(OUTWARD, OUTWARD + "\n"
                        + "    float sdfNormalBlend = os4EdgeBand(edgeDist, "
                        + "max(edgePixelFootprint * 2.0, "
                        + "clamp(minDim * 0.055 * opticalEdgeScale, 2.0, 12.0)), edgeAa);\n"
                        + "    if (sdfNormalBlend > 0.001) {\n"
                        + "        float sdfXp = sdRoundBox(pPx + vec2(edgePixelStep.x, 0.0), "
                        + "halfSz, crMask, u_sminSmoothing);\n"
                        + "        float sdfXn = sdRoundBox(pPx - vec2(edgePixelStep.x, 0.0), "
                        + "halfSz, crMask, u_sminSmoothing);\n"
                        + "        float sdfYp = sdRoundBox(pPx + vec2(0.0, edgePixelStep.y), "
                        + "halfSz, crMask, u_sminSmoothing);\n"
                        + "        float sdfYn = sdRoundBox(pPx - vec2(0.0, edgePixelStep.y), "
                        + "halfSz, crMask, u_sminSmoothing);\n"
                        + "        vec2 sdfEdgeNormal = normalize(vec2(sdfXp - sdfXn, sdfYp - sdfYn));\n"
                        + "        sdfEdgeNormal.y = -sdfEdgeNormal.y;\n"
                        + "        outward = normalize(mix(outward, sdfEdgeNormal, sdfNormalBlend));\n"
                        + "    }\n"
                        + "    vec2 opticalEdgeNormal = outward;")
                .replace(MENISCUS_BLEND,
                        "float menBlend = os4EdgeBand(edgeDist, "
                                + "tw * 0.42 * opticalEdgeScale, edgeAa) "
                                + "* smoothstep(-4.0, 0.0, distMask) * 0.62;")
                .replace(SILHOUETTE_WIDTH,
                        "float silW = clamp(minDim * 0.12 * opticalEdgeScale, 2.5, 34.0);")
                .replace(SILHOUETTE_BAND,
                        "float edgeSil = os4EdgeBand(edgeDist, silW, edgeAa) "
                                + "* smoothstep(-4.5, 0.0, distMask);")
                .replace(BASE_OFFSET,
                        "float os4VolumeMask = os4EdgeBand(edgeDist, os4EdgePx, edgeAa);\n"
                                + "    float os4ThicknessDisplacementPx = mix((os4ThicknessPx - os4EdgePx) * 2.0,\n"
                                + "            os4ThicknessPx * 2.0, os4EdgeDepth);\n"
                                + "    vec3 os4RefIn = refract(-V, N, 1.0 / u_ior);\n"
                                + "    vec3 os4RefOut = (dot(os4RefIn, os4RefIn) < 0.001)\n"
                                + "            ? vec3(0.0) : refract(os4RefIn, -N, u_ior);\n"
                                + "    vec2 os4ThicknessUv = (os4RefOut.xy * os4ThicknessDisplacementPx / u_resolution)\n"
                                + "            * u_displacementScale;\n"
                                + "    vec2 baseOffset = edgeRefractionUv + os4ThicknessUv * os4VolumeMask;")
                .replace(REFLECTION_DIRECTION, "vec2 gDir = opticalEdgeNormal;")
                .replace(REFLECTION_MIX, REFLECTION_MIX + "\n"
                        + "    vec2 os4ReflectUvOffset = opticalEdgeNormal\n"
                        + "            * (os4ReflectOffsetPx * (1.0 - os4EdgeDepth)) / u_resolution\n"
                        + "            * os4VolumeMask;\n"
                        + "    vec2 os4ReflectUv = clamp(uvCenter + os4ReflectUvOffset, vec2(0.0), vec2(1.0));\n"
                        + "    vec3 os4EdgeReflection = texture2D(u_blurredTexture, os4ReflectUv).rgb;\n"
                        + "    if (u_useBlurredTexture != 1) {\n"
                        + "        os4EdgeReflection = texture2D(u_backgroundTexture, os4ReflectUv).rgb;\n"
                        + "    }\n"
                        + "    float os4EdgeReflectionWeight = os4VolumeMask * reflShell * 0.24;\n"
                        + "    color = mix(color, os4EdgeReflection, os4EdgeReflectionWeight);")
                .replace(RIM_BAND_RADIUS,
                        "float bandR = clamp(minDim * bandFracR * opticalEdgeScale * rimBandTight, "
                                + "mix(0.28, 0.65, 1.0 - smallGlass), min(12.0, minDim * 0.1));")
                .replace(RIM_BAND,
                        "float shellRim = os4EdgeBand(edgeDist, bandR, edgeAa) "
                                + "* smoothstep(-2.2, 0.0, distMask);")
                .replace(LIGHTING_NORMAL, "vec2 gN = opticalEdgeNormal;")
                .replace(FACE_SHEEN,
                        "float faceSheenSoft = os4EdgeBand(edgeDist, bandR * 1.8, edgeAa) "
                                + "* smoothstep(-2.0, 0.0, distMask)")
                .replace(PLAIN_HIGHLIGHT,
                        "float plusHL = os4EdgeBand(edgeDist, bandR * 0.95, edgeAa) "
                                + "* u_plainHighlight * u_rimStrength")
                .replace(PLAIN_HIGHLIGHT_ADD, PLAIN_HIGHLIGHT_ADD + "\n"
                        + "\n"
                        + "    // OS4-style finite-width Bloom stroke: real ring area, not a hairline.\n"
                        + "    float os4BloomEdgePx = clamp(minDim * 0.090, 9.0, 28.0);\n"
                        + "    float os4BloomOuter = smoothstep(-edgeAa, edgeAa, edgeDist);\n"
                        + "    float os4BloomInner = smoothstep(os4BloomEdgePx - edgeAa,\n"
                        + "            os4BloomEdgePx + edgeAa, edgeDist);\n"
                        + "    float os4BloomRing = clamp(os4BloomOuter * (1.0 - os4BloomInner), 0.0, 1.0);\n"
                        + "    float os4BloomCross = os4EdgeCurve(clamp(1.0 - edgeDist / max(os4BloomEdgePx, 1.0),\n"
                        + "            0.0, 1.0));\n"
                        + "    os4BloomRing *= mix(0.52, 1.0, os4BloomCross);\n"
                        + "    float os4BloomFacing = dot(opticalEdgeNormal, Lxy);\n"
                        + "    float os4BloomMain = pow(max(os4BloomFacing, 0.0), 1.45) * 0.95;\n"
                        + "    float os4BloomOpposite = pow(max(-os4BloomFacing, 0.0), 1.10) * 0.34;\n"
                        + "    float os4BloomAmbient = 0.16;\n"
                        + "    float os4BloomLight = max(os4BloomAmbient, os4BloomMain + os4BloomOpposite);\n"
                        + "    vec3 os4BloomTint = mix(vec3(0.91, 0.955, 1.035), vec3(1.0),\n"
                        + "            clamp(os4BloomMain, 0.0, 1.0));\n"
                        + "    vec3 os4BloomColor = os4BloomTint * os4BloomLight * u_rimStrength * 0.62;\n"
                        + "    color += os4BloomColor * os4BloomRing;");
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
