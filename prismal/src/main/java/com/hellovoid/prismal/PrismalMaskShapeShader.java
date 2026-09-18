package com.hellovoid.prismal;

/**
 * Extends the canonical Prismal fragment shader with an optional signed-distance shape texture.
 *
 * <p>The optical model is not forked. This adapter only overrides the geometry distance/gradient
 * inputs used by the existing Prismal shader; all Fresnel, refraction, chromatic, specular,
 * soft-edge, caustic, shadow and component-gate logic remains the canonical shader body.</p>
 */
final class PrismalMaskShapeShader {
    private static final String UNIFORM_ANCHOR = "uniform int   u_showNormals;\n";
    private static final String MAIN_ANCHOR = "void main() {\n";
    private static final String GEOMETRY_ANCHOR =
            "    vec2 gradLens = gradSdRoundedRectRealistic(cKy, halfSz, gradRadius);\n";

    private PrismalMaskShapeShader() {}

    static String apply(String source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("source is empty");
        }
        String corrected = replaceExactlyOnce(
                source,
                UNIFORM_ANCHOR,
                UNIFORM_ANCHOR
                        + "uniform sampler2D u_shapeSdfTexture;\n"
                        + "uniform int u_shapeSdfEnabled;\n"
                        + "uniform mat3 u_rootPxToShapeUv;\n"
                        + "uniform float u_shapeSdfRangePx;\n");
        corrected = replaceExactlyOnce(
                corrected,
                MAIN_ANCHOR,
                "float prismalShapeDistAtRoot(vec2 rootPx) {\n"
                        + "    vec2 uv = (u_rootPxToShapeUv * vec3(rootPx, 1.0)).xy;\n"
                        + "    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) {\n"
                        + "        return max(u_shapeSdfRangePx, 1.0);\n"
                        + "    }\n"
                        + "    float encoded = texture2D(u_shapeSdfTexture, uv).a;\n"
                        + "    return (0.5 - encoded) * 2.0 * max(u_shapeSdfRangePx, 1.0);\n"
                        + "}\n\n"
                        + MAIN_ANCHOR);
        corrected = replaceExactlyOnce(
                corrected,
                GEOMETRY_ANCHOR,
                GEOMETRY_ANCHOR
                        + "\n"
                        + "    if (u_shapeSdfEnabled == 1) {\n"
                        + "        vec2 rootPx = vec2(gl_FragCoord.x, u_resolution.y - gl_FragCoord.y);\n"
                        + "        float d0 = prismalShapeDistAtRoot(rootPx);\n"
                        + "        float dL = prismalShapeDistAtRoot(rootPx - vec2(1.0, 0.0));\n"
                        + "        float dR = prismalShapeDistAtRoot(rootPx + vec2(1.0, 0.0));\n"
                        + "        float dT = prismalShapeDistAtRoot(rootPx - vec2(0.0, 1.0));\n"
                        + "        float dB = prismalShapeDistAtRoot(rootPx + vec2(0.0, 1.0));\n"
                        + "        distMask = d0;\n"
                        + "        sdKy = d0;\n"
                        + "        edgeDist = -d0;\n"
                        + "        crMask = 0.0;\n"
                        + "        radCorner = 0.0;\n"
                        + "        gradLens = vec2((dR - dL) * 0.5, -(dB - dT) * 0.5);\n"
                        + "        if (length(gradLens) > 1e-5) gradLens = normalize(gradLens);\n"
                        + "        float hL = getHeightFromDist(dL, tw);\n"
                        + "        float hR = getHeightFromDist(dR, tw);\n"
                        + "        float hT = getHeightFromDist(dT, tw);\n"
                        + "        float hB = getHeightFromDist(dB, tw);\n"
                        + "        hSig = getHeightFromDist(d0, tw);\n"
                        + "        gradHSig = vec2((hR - hL) * 0.5, -(hB - hT) * 0.5);\n"
                        + "        reflShell = smoothstep(clamp(minDim * 0.09, 1.8, 18.0), 0.0, edgeDist)\n"
                        + "                * smoothstep(-3.0, 0.0, distMask);\n"
                        + "        reflShell *= mix(0.78, 0.42, smallGlass);\n"
                        + "        float shapeInset = min(max(u_refractionInset, 0.8), max(minDim * 0.06, 1.6));\n"
                        + "        shapeInset = mix(shapeInset, min(shapeInset, minDim * 0.04), smallGlass);\n"
                        + "        opacity = 1.0 - smoothstep(-shapeInset * 0.5, 0.0, distMask);\n"
                        + "        opacity = mix(opacity, 1.0, smoothstep(0.0, 0.55, edgeDist));\n"
                        + "        if (opacity < 0.001) discard;\n"
                        + "    }\n");
        return corrected;
    }

    private static String replaceExactlyOnce(String source, String anchor, String replacement) {
        int first = source.indexOf(anchor);
        if (first < 0 || source.indexOf(anchor, first + anchor.length()) >= 0) {
            throw new IllegalArgumentException("Expected exactly one Prismal mask-shape anchor: " + anchor);
        }
        return source.substring(0, first) + replacement + source.substring(first + anchor.length());
    }
}
