package com.hellovoid.prismal;

/**
 * Removes known dead expressions and skips texture/OS4 work only when the corresponding
 * contribution is mathematically zero. Does not change a nonzero lens or highlight weight.
 *
 * <p>This is applied after the vendored Prismal, single-edge, optical-edge, component gate and
 * invariant patches, so upstream GLSL stays untouched. The exact anchors intentionally fail
 * closed if any earlier shader rewrite changes its contract.</p>
 */
final class PrismalZeroContributionShader {
    private static final String REFLECTION_DIRECTION =
            "    vec2 gDir = normalize(gradLens + vec2(1e-4));\n";
    private static final String OS4_WEIGHT =
            "    float os4ReflectionWeight = clamp(os4EdgeMask\n"
                    + "            * max(u_os4ReflectionStrength, 0.0), 0.0, 1.0);";

    private PrismalZeroContributionShader() {}

    static String apply(String fragment) {
        if (fragment == null) throw new IllegalArgumentException("fragment == null");

        // The reflection shell is exactly zero on the broad interiors of large widgets.
        // Avoid both the displaced UV arithmetic and one extra background texture read there.
        String result = replaceOnce(fragment, REFLECTION_DIRECTION, "", "reflection direction");
        result = guardRange(result,
                "    vec2 reflUv = clamp(",
                "    color = mix(color, reflSample, reflW);",
                "reflW > 0.0", "    " + REFLECTION_DIRECTION.strip() + "\n",
                "legacy reflection sample");

        // OS4 reflection has its own narrow SDF edge mask. Calculate the scalar weight before
        // any sampler operations and skip both the offset and the fetch if that weight is zero.
        result = replaceOnce(result, OS4_WEIGHT, "", "OS4 reflection weight");
        result = guardRange(result,
                "    vec2 os4ReflectUvOffset = os4EdgeNormal3.xy",
                "    color = mix(color, os4EdgeReflection, os4ReflectionWeight);",
                "os4ReflectionWeight > 0.0",
                "    " + OS4_WEIGHT + "\n",
                "OS4 reflection sample");

        // Both acos() calls, three dots, luma and associated lighting math affect output only
        // inside an enabled OS4 edge mask. The debug-normal path also uses os4SoftLight, so it
        // must remain declared/zero-initialized outside the branch.
        result = replaceOnce(result,
                "    float os4SoftLight = clamp(os4Directional * os4EdgeMask, 0.0, 1.0);",
                "    os4SoftLight = clamp(os4Directional * os4EdgeMask, 0.0, 1.0);",
                "OS4 light assignment");
        result = guardRange(result,
                "    vec3 os4LightDir3 = u_invariantOs4LightDir;",
                "    color += vec3(os4SoftLight\n"
                        + "            * (0.42 + max(u_os4ReflectionLighten, 0.0) * os4DarkResponse));",
                "os4EdgeMask > 0.0",
                "    float os4SoftLight = 0.0;\n",
                "OS4 edge-only directional lighting");

        // These three upstream intermediates have no remaining consumer after the single-edge
        // refraction transform. Removing them is code hygiene: GLES drivers likely DCE already.
        result = replaceOnce(result,
                "    float edgePunch = mix(1.0, 1.12, smoothstep(74.0, 200.0, minDim));\n",
                "", "unused edge punch init");
        result = replaceOnce(result,
                "    edgePunch = mix(edgePunch, 1.0, smallGlass * 0.85);\n",
                "", "unused edge punch adjustment");
        result = replaceOnce(result, "    float Fr = fresCtl;\n", "", "unused Fresnel alias");
        // The falloff configuration expression also has zero users after the single-edge
        // replacement. Do not silently change the lens formula or the persisted GUI key:
        // the disconnected setting is documented separately for an explicit functional fix.
        // SingleEdge's text-block replacement starts inside the original statement's
        // four-space indentation: its *second* generated line has no leading indentation.
        // Match the expression text rather than assuming the original layout.
        result = replaceOnce(result,
                "float edgeFalloffPower = max(0.02, max(u_edgeRefractionFalloff, 0.0) * 0.205);\n"
                        + "float dropLens = pow(smoothstep(refractionHeight, 0.0, edgeDist), edgeFalloffPower);\n",
                "", "unused old lens falloff");
        result = replaceOnce(result,
                "// 400% retains the original 0.82 exponent, with a responsive full GUI range.\n",
                "", "unused lens falloff comment");
        return result;
    }

    private static String guardRange(String shader, String first, String last,
            String condition, String before, String label) {
        int start = shader.indexOf(first);
        if (start < 0 || shader.indexOf(first, start + first.length()) >= 0) {
            throw new IllegalStateException(label + " start anchor changed");
        }
        int end = shader.indexOf(last, start);
        if (end < 0 || shader.indexOf(last, end + last.length()) >= 0) {
            throw new IllegalStateException(label + " end anchor changed");
        }
        end += last.length();
        String block = shader.substring(start, end);
        return shader.substring(0, start) + before
                + "    if (" + condition + ") {\n"
                + "    " + block.replace("\n", "\n    ")
                + "\n    }" + shader.substring(end);
    }

    private static String replaceOnce(String source, String from, String to, String label) {
        int first = source.indexOf(from);
        if (first < 0 || source.indexOf(from, first + from.length()) >= 0) {
            throw new IllegalStateException(label + " upstream contract changed");
        }
        return source.substring(0, first) + to + source.substring(first + from.length());
    }
}
