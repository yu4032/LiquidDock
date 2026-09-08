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
    private static final String SINGLE_EDGE_BASE_OFFSET = "vec2 baseOffset = edgeRefractionUv;";
    private static final String UPSTREAM_BASE_OFFSET =
            "vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;";
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

    private static final String OS4_EDGE_UNIFORMS = """
            uniform float u_os4SoftEdgeEnabled;
            uniform float u_os4EdgeWidthPx;
            uniform float u_os4ReflectOffsetPx;
            uniform float u_os4ReflectionStrength;
            uniform float u_os4ReflectionLighten;
            uniform float u_os4DirectionalAngleRange;
            uniform float u_os4DirectionalIntensity;
            uniform float u_os4DirectionalOppositeIntensity;
            """;

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
        String baseOffsetAnchor = resolveBaseOffsetAnchor(source);
        requireSingle(source, REFLECTION_MIX);
        requireSingle(source, RIM_BAND_RADIUS);
        requireSingle(source, RIM_BAND);
        requireSingle(source, REFLECTION_DIRECTION);
        requireSingle(source, LIGHTING_NORMAL);
        requireSingle(source, FACE_SHEEN);
        requireSingle(source, PLAIN_HIGHLIGHT);
        requireSingle(source, PLAIN_HIGHLIGHT_ADD);

        String baseOffsetReplacement = volumetricBaseOffset(baseOffsetAnchor);

        return source
                .replace(PRECISION,
                        "#extension GL_OES_standard_derivatives : enable\n\n"
                                + PRECISION + "\n\n" + OS4_EDGE_UNIFORMS + "\n" + OS4_EDGE_HELPERS)
                .replace(SHAPE_POSITION, SHAPE_POSITION + "\n"
                        + "    vec2 edgePixelStep = vec2(\n"
                        + "            max(length(dFdx(pPx)), 0.5),\n"
                        + "            max(length(dFdy(pPx)), 0.5));\n"
                        + "    float edgePixelFootprint = max(edgePixelStep.x, edgePixelStep.y);")
                .replace(EDGE_DISTANCE, EDGE_DISTANCE + "\n"
                        + "    float opticalEdgeScale = clamp(u_highlightWidth, 0.5, 3.0);\n"
                        + "    float edgeAa = max(fwidth(distMask), 0.75);\n"
                        + "    edgeAa = max(edgeAa, edgePixelFootprint * 0.55);\n"
                        + "    // OS4 mode is independent from all nine legacy highlight component gates.\n"
                        + "    float os4Mode = step(0.5, u_os4SoftEdgeEnabled);\n"
                        + "    // OS4 edge width controls the internal optical band, never output alpha.\n"
                        + "    float os4EdgePx = u_os4EdgeWidthPx > 0.0\n"
                        + "            ? u_os4EdgeWidthPx : clamp(minDim * 0.060, 6.0, 18.0);\n"
                        + "    float os4ThicknessPx = max(u_glassThickness, os4EdgePx + 6.0);\n"
                        + "    float os4ReflectOffsetPx = u_os4ReflectOffsetPx > 0.0\n"
                        + "            ? u_os4ReflectOffsetPx : clamp(os4ThicknessPx * 0.38, 4.0, 14.0);\n"
                        + "    float os4EdgeT = clamp(edgeDist / max(os4EdgePx, 1.0), 0.0, 1.0);\n"
                        + "    float os4EdgeRemain = 1.0 - os4EdgeT;\n"
                        + "    float os4EdgeDepth = os4EdgeT;")
                .replace(REFLECTION_SHELL,
                        "float reflShell = os4EdgeBand(edgeDist, "
                                + "clamp(minDim * 0.09 * opticalEdgeScale, 1.8, 18.0), edgeAa) "
                                + "* smoothstep(-3.0, 0.0, distMask);")
                .replace(OPACITY,
                        "float opacity = 1.0 - smoothstep(-edgeAa, edgeAa, distMask);")
                .replace(OUTWARD, OUTWARD + "\n"
                        + "    // OS4 owns one continuous rounded-rect SDF normal; legacy outward stays independent.\n"
                        + "    float sdfXp = sdRoundBox(pPx + vec2(edgePixelStep.x, 0.0), "
                        + "halfSz, crMask, u_sminSmoothing);\n"
                        + "    float sdfXn = sdRoundBox(pPx - vec2(edgePixelStep.x, 0.0), "
                        + "halfSz, crMask, u_sminSmoothing);\n"
                        + "    float sdfYp = sdRoundBox(pPx + vec2(0.0, edgePixelStep.y), "
                        + "halfSz, crMask, u_sminSmoothing);\n"
                        + "    float sdfYn = sdRoundBox(pPx - vec2(0.0, edgePixelStep.y), "
                        + "halfSz, crMask, u_sminSmoothing);\n"
                        + "    vec2 sdfEdgeGradient = 0.5 * vec2(\n"
                        + "            (sdfXp - sdfXn) / max(edgePixelStep.x, 1e-3),\n"
                        + "            (sdfYp - sdfYn) / max(edgePixelStep.y, 1e-3));\n"
                        + "    sdfEdgeGradient.y = -sdfEdgeGradient.y;\n"
                        + "    vec3 os4EdgeNormal3 = normalize(vec3(sdfEdgeGradient, 1.0));")
                .replace(MENISCUS_BLEND,
                        "float menBlend = os4EdgeBand(edgeDist, "
                                + "tw * 0.42 * opticalEdgeScale, edgeAa) "
                                + "* smoothstep(-4.0, 0.0, distMask) * 0.62;")
                .replace(SILHOUETTE_WIDTH,
                        "float silW = clamp(minDim * 0.12 * opticalEdgeScale, 2.5, 34.0);")
                .replace(SILHOUETTE_BAND,
                        "float edgeSil = os4EdgeBand(edgeDist, silW, edgeAa) "
                                + "* smoothstep(-4.5, 0.0, distMask);")
                .replace(baseOffsetAnchor, baseOffsetReplacement)
                .replace(REFLECTION_DIRECTION, "vec2 gDir = outward;")
                .replace(REFLECTION_MIX, REFLECTION_MIX + "\n"
                        + "    vec2 os4ReflectUvOffset = os4EdgeNormal3.xy\n"
                        + "            * (2.0 * os4EdgeNormal3.z)\n"
                        + "            * os4ReflectOffsetPx * os4EdgeRemain / u_resolution\n"
                        + "            * os4VolumeMask;\n"
                        + "    vec2 os4ReflectUv = clamp(uvCenter + os4ReflectUvOffset, "
                        + "vec2(0.0), vec2(1.0));\n"
                        + "    vec3 os4EdgeReflection = texture2D(u_blurredTexture, os4ReflectUv).rgb;\n"
                        + "    if (u_useBlurredTexture != 1) {\n"
                        + "        os4EdgeReflection = texture2D(u_backgroundTexture, os4ReflectUv).rgb;\n"
                        + "    }\n"
                        + "    float os4ReflectMask = (1.0 - os4EdgeCurve(os4EdgeT)) * os4VolumeMask;\n"
                        + "    float os4EdgeReflectionWeight = clamp(os4ReflectMask "
                        + "* max(u_os4ReflectionStrength, 0.0), 0.0, 1.0);\n"
                        + "    color = mix(color, os4EdgeReflection, os4EdgeReflectionWeight);")
                .replace(RIM_BAND_RADIUS,
                        "float bandR = clamp(minDim * bandFracR * opticalEdgeScale * rimBandTight, "
                                + "mix(0.28, 0.65, 1.0 - smallGlass), min(12.0, minDim * 0.1));")
                .replace(RIM_BAND,
                        "float shellRim = os4EdgeBand(edgeDist, bandR, edgeAa) "
                                + "* smoothstep(-2.2, 0.0, distMask);")
                .replace(LIGHTING_NORMAL, "vec2 gN = outward;")
                .replace(FACE_SHEEN,
                        "float faceSheenSoft = os4EdgeBand(edgeDist, bandR * 1.8, edgeAa) "
                                + "* smoothstep(-2.0, 0.0, distMask)")
                .replace(PLAIN_HIGHLIGHT,
                        "float plusHL = os4EdgeBand(edgeDist, bandR * 0.95, edgeAa) "
                                + "* u_plainHighlight * u_rimStrength")
                .replace(PLAIN_HIGHLIGHT_ADD, PLAIN_HIGHLIGHT_ADD + "\n"
                        + "\n"
                        + "    // Extracted OS4 model: directional light modulates the current backdrop-driven glass.\n"
                        + "    vec3 os4LightDir3 = normalize(vec3(Lxy, 1.0));\n"
                        + "    float os4A = clamp(dot(os4EdgeNormal3, os4LightDir3), -1.0, 1.0);\n"
                        + "    float os4B = clamp(2.0 * os4EdgeNormal3.z * os4LightDir3.z - os4A, -1.0, 1.0);\n"
                        + "    float os4AngleRange = max(u_os4DirectionalAngleRange, 0.05);\n"
                        + "    float os4MainAngle = acos(clamp(os4A, 0.0, 1.0));\n"
                        + "    float os4OppositeAngle = acos(clamp(os4B, 0.0, 1.0));\n"
                        + "    float os4MainFalloff = max(1.0 - os4MainAngle / (3.14159265 * os4AngleRange), 0.0);\n"
                        + "    float os4OppositeFalloff = max(1.0 - os4OppositeAngle / (3.14159265 * os4AngleRange), 0.0);\n"
                        + "    float os4DirectionalMain = max(os4A, 0.0) "
                        + "* max(u_os4DirectionalIntensity, 0.0) * os4MainFalloff;\n"
                        + "    float os4DirectionalOpposite = max(os4B, 0.0) "
                        + "* max(u_os4DirectionalOppositeIntensity, 0.0) * os4OppositeFalloff;\n"
                        + "    float os4EdgeMask = (1.0 - os4EdgeCurve(os4EdgeT)) * os4VolumeMask;\n"
                        + "    float os4SoftLight = clamp((os4DirectionalMain + os4DirectionalOpposite) "
                        + "* os4EdgeMask, 0.0, 1.0);\n"
                        + "    float os4DirectionalGain = 1.0 + smoothstep(0.0, 1.0, os4SoftLight);\n"
                        + "    color *= os4DirectionalGain;\n"
                        + "    float os4Luma = dot(color, vec3(0.2126, 0.7152, 0.0722));\n"
                        + "    float os4DarkResponse = 1.0 - smoothstep(0.35, 0.92, os4Luma);\n"
                        + "    color += vec3(u_os4ReflectionLighten * os4DarkResponse\n"
                        + "            * max(os4DirectionalGain - 1.0, 0.0));");
    }

    private static String resolveBaseOffsetAnchor(String source) {
        int singleEdgeCount = countOccurrences(source, SINGLE_EDGE_BASE_OFFSET);
        int upstreamCount = countOccurrences(source, UPSTREAM_BASE_OFFSET);
        if (singleEdgeCount + upstreamCount != 1) {
            throw new IllegalArgumentException(
                    "Expected exactly one Prismal base-offset stage anchor; singleEdge="
                            + singleEdgeCount + " upstream=" + upstreamCount);
        }
        return singleEdgeCount == 1 ? SINGLE_EDGE_BASE_OFFSET : UPSTREAM_BASE_OFFSET;
    }

    private static String volumetricBaseOffset(String anchor) {
        String originalExpression = anchor.equals(SINGLE_EDGE_BASE_OFFSET)
                ? "edgeRefractionUv"
                : "lensDeltaUv + snellOff + bulgeUv";
        return "float os4VolumeMask = os4EdgeBand(edgeDist, os4EdgePx, edgeAa) * os4Mode;\n"
                + "    float os4ThicknessDisplacementPx = mix((os4ThicknessPx - os4EdgePx) * 2.0,\n"
                + "            os4ThicknessPx * 2.0, os4EdgeDepth);\n"
                + "    vec3 os4RefIn = refract(-V, N, 1.0 / u_ior);\n"
                + "    vec3 os4RefOut = (dot(os4RefIn, os4RefIn) < 0.001)\n"
                + "            ? vec3(0.0) : refract(os4RefIn, -N, u_ior);\n"
                + "    vec2 os4ThicknessUv = (os4RefOut.xy * os4ThicknessDisplacementPx / u_resolution)\n"
                + "            * u_displacementScale;\n"
                + "    vec2 baseOffset = " + originalExpression
                + " + os4ThicknessUv * os4VolumeMask;";
    }

    private static int countOccurrences(String source, String anchor) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = source.indexOf(anchor, from);
            if (at < 0) return count;
            count++;
            from = at + anchor.length();
        }
    }

    private static void requireSingle(String source, String anchor) {
        if (countOccurrences(source, anchor) != 1) {
            throw new IllegalArgumentException(
                    "Expected exactly one Prismal optical-edge anchor: " + anchor);
        }
    }

    private PrismalOpticalEdgeShader() {}
}
