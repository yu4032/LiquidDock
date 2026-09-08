package com.hellovoid.prismal;

/** Scales Prismal's physical optical edge bands without drawing a separate outline. */
public final class PrismalOpticalEdgeShader {
    private static final String PRECISION = "precision highp float;";
    private static final String SHAPE_POSITION = "vec2 pPx = v_shapeCoord * u_glassSize;";
    private static final String EDGE_DISTANCE = "float edgeDist = -distMask;";
    private static final String OPACITY =
            "float opacity = 1.0 - smoothstep(-inset * 0.5, 0.0, distMask);";
    private static final String OUTWARD =
            "vec2 outward = (length(gradLens) > 1e-4) ? normalize(gradLens) : vec2(0.0, 1.0);";
    private static final String REFLECTION_MIX = "color = mix(color, reflSample, reflW);";
    private static final String PLAIN_HIGHLIGHT_ADD =
            "color += plusHL * vec3(0.99, 0.995, 1.0);";

    private static final String OS4_EDGE_UNIFORMS = """
            uniform float u_os4EdgeEnabled;
            """;

    private static final String OS4_EDGE_HELPERS = """
            float os4EdgeCurve(float t) {
                t = clamp(t, 0.0, 1.0);
                if (t >= 0.85) return 1.0;
                float shoulder = clamp(mix(0.5, 1.0, sqrt(t)), 0.0, 1.0);
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
        requireSingle(source, OUTWARD);
        requireSingle(source, REFLECTION_MIX);
        requireSingle(source, PLAIN_HIGHLIGHT_ADD);
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
                        + "    float os4EdgeWidthPx = clamp(minDim * 0.060, 6.0, 18.0);\n"
                        + "    float os4EdgeT = clamp(edgeDist / max(os4EdgeWidthPx, 1.0), 0.0, 1.0);\n"
                        + "    float os4EdgeMask = os4EdgeBand(edgeDist, os4EdgeWidthPx, edgeAa)\n"
                        + "            * step(0.5, u_os4EdgeEnabled);")
                .replace(OPACITY,
                        "float opacity = 1.0 - smoothstep(-edgeAa, edgeAa, distMask);")
                .replace(OUTWARD, OUTWARD + "\n"
                        + "    float sdfXp = sdRoundBox(pPx + vec2(edgePixelStep.x, 0.0),\n"
                        + "            halfSz, crMask, u_sminSmoothing);\n"
                        + "    float sdfXn = sdRoundBox(pPx - vec2(edgePixelStep.x, 0.0),\n"
                        + "            halfSz, crMask, u_sminSmoothing);\n"
                        + "    float sdfYp = sdRoundBox(pPx + vec2(0.0, edgePixelStep.y),\n"
                        + "            halfSz, crMask, u_sminSmoothing);\n"
                        + "    float sdfYn = sdRoundBox(pPx - vec2(0.0, edgePixelStep.y),\n"
                        + "            halfSz, crMask, u_sminSmoothing);\n"
                        + "    vec2 sdfEdgeGradient = 0.5 * vec2(\n"
                        + "            (sdfXp - sdfXn) / max(edgePixelStep.x, 1e-3),\n"
                        + "            (sdfYp - sdfYn) / max(edgePixelStep.y, 1e-3));\n"
                        + "    sdfEdgeGradient.y = -sdfEdgeGradient.y;\n"
                        + "    vec3 os4EdgeNormal3 = normalize(vec3(sdfEdgeGradient, 1.0));")
                .replace(REFLECTION_MIX, REFLECTION_MIX + "\n"
                        + "    float os4ReflectOffsetPx = clamp(u_glassThickness * 0.38, 4.0, 14.0);\n"
                        + "    vec2 os4ReflectUvOffset = os4EdgeNormal3.xy\n"
                        + "            * (2.0 * os4EdgeNormal3.z)\n"
                        + "            * os4ReflectOffsetPx * (1.0 - os4EdgeT) / u_resolution\n"
                        + "            * os4EdgeMask;\n"
                        + "    vec2 os4ReflectUv = clamp(uvCenter + os4ReflectUvOffset,\n"
                        + "            vec2(0.0), vec2(1.0));\n"
                        + "    vec3 os4EdgeReflection = texture2D(u_blurredTexture, os4ReflectUv).rgb;\n"
                        + "    if (u_useBlurredTexture != 1) {\n"
                        + "        os4EdgeReflection = texture2D(u_backgroundTexture, os4ReflectUv).rgb;\n"
                        + "    }\n"
                        + "    float os4ReflectionWeight = clamp(os4EdgeMask * 0.28, 0.0, 1.0);\n"
                        + "    color = mix(color, os4EdgeReflection, os4ReflectionWeight);")
                .replace(PLAIN_HIGHLIGHT_ADD, PLAIN_HIGHLIGHT_ADD + "\n"
                        + "    vec3 os4LightDir3 = normalize(vec3(Lxy, 1.0));\n"
                        + "    float os4MainFacing = max(dot(os4EdgeNormal3, os4LightDir3), 0.0);\n"
                        + "    float os4OppositeFacing = max(\n"
                        + "            dot(vec3(-os4EdgeNormal3.xy, os4EdgeNormal3.z),\n"
                        + "                    os4LightDir3), 0.0);\n"
                        + "    float os4Directional = os4MainFacing * 0.42\n"
                        + "            + os4OppositeFacing * 0.14;\n"
                        + "    float os4SoftLight = clamp(os4Directional * os4EdgeMask, 0.0, 1.0);\n"
                        + "    float os4Luma = dot(color, vec3(0.2126, 0.7152, 0.0722));\n"
                        + "    float os4DarkResponse = 1.0 - smoothstep(0.35, 0.92, os4Luma);\n"
                        + "    color += vec3(os4SoftLight * (0.42 + 0.16 * os4DarkResponse));")
                .replace("minDim * 0.09", "minDim * 0.09 * opticalEdgeScale")
                .replace("tw * 0.42", "tw * 0.42 * opticalEdgeScale")
                .replace("minDim * 0.12", "minDim * 0.12 * opticalEdgeScale")
                .replace("bandFracR * rimBandTight",
                        "bandFracR * opticalEdgeScale * rimBandTight");
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
