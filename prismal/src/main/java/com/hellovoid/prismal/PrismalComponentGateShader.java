package com.hellovoid.prismal;

/**
 * Adds per-draw gates to optional highlight families.
 *
 * <p>Current LiquidDock output always calls
 * {@link PrismalHighlightProfile#withOs4EdgeReplacingLegacyEdge()}, so the legacy specular/rim
 * families are normally disabled. Gate their whole arithmetic blocks, not only the final color
 * additions: otherwise every fragment still pays the old pow/exp/normalize cost before a false
 * uniform branch throws the result away. Profiles that enable any legacy family keep the exact
 * upstream calculations and per-component adds.</p>
 */
final class PrismalComponentGateShader {
    private static final String DECLARATION_ANCHOR = "uniform float u_glowStrength;";
    private static final String COMPONENT_UNIFORMS = """
            uniform float u_componentSkyHaze;
            uniform float u_componentSpecular;
            uniform float u_componentLitRim;
            uniform float u_componentOppositeRim;
            uniform float u_componentCornerRim;
            uniform float u_componentFaceSheen;
            uniform float u_componentPlainHighlight;
            uniform float u_componentCaustics;
            uniform float u_componentPressGlow;
            """;

    private static final String SECONDARY_LIGHT_VECTORS = """
                vec3 Ls = normalize(vec3(-u_lightDir.x * 0.62 + 0.41, -u_lightDir.y * 0.62 + 0.33, 0.74));
                vec3 Hp = normalize(Lp + V);
                vec3 Hs = normalize(Ls + V);

            """;
    private static final String LXY =
            "    vec2 Lxy = normalize(u_lightDir + vec2(1e-5));\n";

    private PrismalComponentGateShader() {}

    static String apply(String fragment) {
        if (fragment == null) throw new IllegalArgumentException("fragment == null");
        String corrected = replaceExactlyOnce(
                fragment,
                DECLARATION_ANCHOR,
                DECLARATION_ANCHOR + "\n" + COMPONENT_UNIFORMS,
                "Prismal component uniform anchor");

        // Sky haze has no value when disabled, so skip its pow/mix work as a unit.
        corrected = wrapRangeExactlyOnce(
                corrected,
                "    vec3 skyHaze = vec3(0.88, 0.93, 1.02);",
                "    color = mix(color, mix(color, skyHaze, 0.55 + 0.1 * fresCtl), skyW);",
                "u_componentSkyHaze > 0.5",
                null,
                "sky haze block");

        // Lp remains outside because the optional caustic family also consumes it. The secondary
        // light and half-vectors are specular-only and move inside the specular branch.
        corrected = replaceExactlyOnce(
                corrected, SECONDARY_LIGHT_VECTORS, "", "secondary specular light vectors");
        corrected = wrapRangeExactlyOnce(
                corrected,
                "    float sh = max(u_shininess, 1.0);",
                "    color += (specP + specS) * vec3(0.99, 0.993, 1.0);",
                "u_componentSpecular > 0.5",
                "        vec3 Ls = normalize(vec3(-u_lightDir.x * 0.62 + 0.41, "
                        + "-u_lightDir.y * 0.62 + 0.33, 0.74));\n"
                        + "        vec3 Hp = normalize(Lp + V);\n"
                        + "        vec3 Hs = normalize(Ls + V);",
                "specular block");

        // OS4 directional reflection still uses Lxy, so compute that one shared vector outside.
        // Everything from Vn through the old plain highlight belongs only to legacy edge families.
        corrected = replaceExactlyOnce(corrected, LXY, "", "legacy Lxy declaration");
        corrected = replaceExactlyOnce(
                corrected,
                "    vec3 Vn = normalize(V);",
                LXY + "\n    vec3 Vn = normalize(V);",
                "OS4 shared Lxy placement");
        String anyLegacyRim = "u_componentLitRim > 0.5"
                + " || u_componentOppositeRim > 0.5"
                + " || u_componentCornerRim > 0.5"
                + " || u_componentFaceSheen > 0.5"
                + " || u_componentPlainHighlight > 0.5";
        corrected = wrapRangeExactlyOnce(
                corrected,
                "    vec3 Vn = normalize(V);",
                "    color += plusHL * vec3(0.99, 0.995, 1.0);",
                anyLegacyRim,
                null,
                "legacy rim block");

        // Inside the shared legacy branch, preserve independent per-component controls.
        corrected = gateExactlyOnce(corrected,
                "color += hiSoft * rimLitSide * rimScale;",
                "u_componentLitRim", "lit rim");
        corrected = gateExactlyOnce(corrected,
                "color += mix(hiVeil, oppTint, 0.42) * rimOpposite * rimScale;",
                "u_componentOppositeRim", "opposite rim");
        corrected = gateExactlyOnce(corrected,
                "color += hiSoft * rimCorner * rimScale;",
                "u_componentCornerRim", "corner rim");
        corrected = gateExactlyOnce(corrected,
                "color += hiSoft * faceSheenSoft * (0.48 + 0.52 * height) * rimScale;",
                "u_componentFaceSheen", "face sheen");
        corrected = gateExactlyOnce(corrected,
                "color += plusHL * vec3(0.99, 0.995, 1.0);",
                "u_componentPlainHighlight", "plain highlight");

        // These existing blocks already have natural runtime branches; extend the condition so
        // disabled profiles skip their normalize/pow/length work too.
        corrected = replaceExactlyOnce(
                corrected,
                "if (u_causticIntensity > 0.001) {",
                "if (u_componentCaustics > 0.5 && u_causticIntensity > 0.001) {",
                "caustic block gate");
        // Deep large-surface cores have very low dome height; do not let that suppress
        // nearly all caustic illumination while the curved edge keeps its original weight.
        corrected = replaceExactlyOnce(
                corrected,
                "float caust = pow(max(causticDot, 0.0), 7.0) * u_causticIntensity * height;",
                "float caust = pow(max(causticDot, 0.0), 7.0) * u_causticIntensity "
                        + "* mix(height, 1.0, 0.30 * coreBlend);",
                "caustic core coverage");
        corrected = replaceExactlyOnce(
                corrected,
                "if (pressGlow > 0.001) {",
                "if (u_componentPressGlow > 0.5 && pressGlow > 0.001) {",
                "press glow block gate");
        return corrected;
    }

    private static String wrapRangeExactlyOnce(
            String source,
            String startAnchor,
            String endAnchor,
            String condition,
            String prefixInside,
            String label) {
        int start = source.indexOf(startAnchor);
        if (start < 0 || source.indexOf(startAnchor, start + startAnchor.length()) >= 0) {
            throw new IllegalStateException("Prismal " + label + " start contract changed");
        }
        int end = source.indexOf(endAnchor, start);
        if (end < 0 || source.indexOf(endAnchor, end + endAnchor.length()) >= 0) {
            throw new IllegalStateException("Prismal " + label + " end contract changed");
        }
        end += endAnchor.length();
        String block = source.substring(start, end);
        StringBuilder wrapped = new StringBuilder(block.length() + 256);
        wrapped.append("    if (").append(condition).append(") {\n");
        if (prefixInside != null && !prefixInside.isEmpty()) {
            wrapped.append(prefixInside).append("\n");
        }
        String[] lines = block.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            wrapped.append("    ").append(lines[i]);
            if (i + 1 < lines.length) wrapped.append('\n');
        }
        wrapped.append("\n    }");
        return source.substring(0, start) + wrapped + source.substring(end);
    }

    private static String gateExactlyOnce(
            String source, String statement, String uniform, String label) {
        return replaceExactlyOnce(
                source,
                statement,
                "if (" + uniform + " > 0.5) { " + statement + " }",
                "Prismal " + label + " component");
    }

    private static String replaceExactlyOnce(
            String source, String oldText, String newText, String label) {
        int first = source.indexOf(oldText);
        if (first < 0 || source.indexOf(oldText, first + oldText.length()) >= 0) {
            throw new IllegalStateException(label + " upstream contract changed");
        }
        return source.substring(0, first) + newText + source.substring(first + oldText.length());
    }
}
