package com.hellovoid.prismal;

/**
 * Expands only the raster primitive around the Prismal SDF silhouette.
 *
 * <p>The upstream vertex quad ends exactly at +/-0.5 of {@code u_glassSize}. On the straight
 * sides of a rounded rectangle that is also exactly where {@code distMask == 0}. LiquidDock's
 * derivative AA is centered on that zero contour, so half of the AA footprint otherwise lies
 * outside the triangle primitive and can never produce fragments. A two-logical-pixel guard on
 * each side lets the fragment shader evaluate that outer half without changing the SDF geometry:
 * both screen position and {@code v_shapeCoord} are expanded by the same scale.</p>
 */
final class PrismalRasterGuardShader {
    private static final String SCREEN_POSITION =
            "vec2 screenPos = u_mousePos + a_position * u_glassSize;";
    private static final String SHAPE_COORD = "v_shapeCoord = a_position;";

    private static final String GUARDED_SCREEN_POSITION = """
            vec2 safeGlassSize = max(u_glassSize, vec2(1.0));
            vec2 rasterScale = (safeGlassSize + vec2(4.0)) / safeGlassSize;
            vec2 rasterPosition = a_position * rasterScale;
            vec2 screenPos = u_mousePos + rasterPosition * u_glassSize;
            """;

    private PrismalRasterGuardShader() {}

    static String apply(String upstreamVertex) {
        if (upstreamVertex == null) throw new IllegalArgumentException("upstreamVertex == null");
        String guarded = replaceExactlyOnce(
                upstreamVertex, SCREEN_POSITION, GUARDED_SCREEN_POSITION,
                "Prismal raster screen position");
        return replaceExactlyOnce(
                guarded, SHAPE_COORD, "v_shapeCoord = rasterPosition;",
                "Prismal raster shape coordinate");
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
