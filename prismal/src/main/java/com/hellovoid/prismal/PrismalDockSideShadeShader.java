package com.hellovoid.prismal;

/**
 * Opt-in Dock BODY shading inserted only in the final Prismal color stage.
 * Reuses OS4's SDF gradient, edge-AA and Prismal light vector. No second pass.
 *
 * The existing directional OS4 highlights remain unchanged on all four sides;
 * dark contour is limited to near-vertical LEFT/RIGHT sides and vanishes on
 * surfaces facing the light. Native MiShadow stays below the entire Dock.
 */
final class PrismalDockSideShadeShader {
    private static final String COLOR =
            "gl_FragColor = vec4(color, opacity * u_transmittance);";
    private static final String UNIFORM = "uniform float u_os4EdgeEnabled;";

    private PrismalDockSideShadeShader() {}

    static String apply(String source) {
        requireOnce(source, UNIFORM);
        requireOnce(source, COLOR);
        if (!source.contains("os4SdfGradient") || !source.contains("os4EdgeBand(")
                || !source.contains("u_lightDir") || !source.contains("edgeAa")) {
            throw new IllegalStateException("OS4 Dock side shade prerequisites changed");
        }
        return source.replace(UNIFORM, UNIFORM + """
                
                uniform float u_dockSideShadeAlpha;
                uniform float u_dockSideShadeWidthPx;
                uniform float u_dockSideShadeLightSuppression;
                uniform float u_dockSideShadeMinCos;
                """).replace(COLOR, """
                    if (u_dockSideShadeAlpha > 0.001) {
                        vec2 sideNormal = normalize(os4SdfGradient + vec2(0.00001));
                        // Left/right normals only: upper/lower edge stays optical highlight.
                        // Angular aperture centered on left/right edge normals.
                        // A smaller cosine means a longer dark contour around the corners.
                        // Width of the transition remains fixed in cosine space; the
                        // upper/lower center stays entirely optical (no dark strip).
                        float verticalSide = smoothstep(
                                u_dockSideShadeMinCos,
                                min(1.0, u_dockSideShadeMinCos + 0.10),
                                abs(sideNormal.x));
                        vec2 sourceDir = normalize(u_lightDir + vec2(0.00001));
                        float illuminated = max(dot(sideNormal, sourceDir), 0.0);
                        float visibleDark = 1.0 - clamp(
                                illuminated * u_dockSideShadeLightSuppression, 0.0, 1.0);
                        float band = os4EdgeBand(edgeDist,
                                max(u_dockSideShadeWidthPx, 1.0), edgeAa);
                        float darkness = clamp(verticalSide * visibleDark * band
                                * u_dockSideShadeAlpha, 0.0, 0.95);
                        color *= 1.0 - darkness;
                    }
                    """ + COLOR);
    }

    private static void requireOnce(String text, String pattern) {
        if (text == null) throw new IllegalArgumentException("shader == null");
        int first = text.indexOf(pattern);
        if (first < 0 || text.indexOf(pattern, first + pattern.length()) >= 0) {
            throw new IllegalStateException("Dock side shade anchor changed: " + pattern);
        }
    }
}
