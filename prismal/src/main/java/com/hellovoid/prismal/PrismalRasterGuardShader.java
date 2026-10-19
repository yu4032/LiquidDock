package com.hellovoid.prismal;

/**
 * Keep the proven two-pixel Prismal AA raster guard byte-for-byte when edge
 * effects are disabled. The optional shader gets a separate expandable vertex
 * variant; it must never be required for baseline glass initialization.
 */
final class PrismalRasterGuardShader {
    private static final String SCREEN_POSITION =
            "vec2 screenPos = u_mousePos + a_position * u_glassSize;";
    private static final String SHAPE_COORD = "v_shapeCoord = a_position;";
    private static final String RASTER_SCALE =
            "vec2 rasterScale = (safeGlassSize + vec2(4.0)) / safeGlassSize;";

    private static final String GUARDED_SCREEN_POSITION = """
            vec2 safeGlassSize = max(u_glassSize, vec2(1.0));
            vec2 rasterScale = (safeGlassSize + vec2(4.0)) / safeGlassSize;
            vec2 rasterPosition = a_position * rasterScale;
            vec2 screenPos = u_mousePos + rasterPosition * u_glassSize;
            """;

    private PrismalRasterGuardShader() {}

    /** Original AA vertex path: does not depend on any new edge-effect uniform. */
    static String apply(String upstreamVertex) {
        if (upstreamVertex == null) throw new IllegalArgumentException("upstreamVertex == null");
        String guarded = replaceExactlyOnce(upstreamVertex, SCREEN_POSITION,
                GUARDED_SCREEN_POSITION, "Prismal raster screen position");
        return replaceExactlyOnce(guarded, SHAPE_COORD,
                "v_shapeCoord = rasterPosition;", "Prismal raster shape coordinate");
    }

    /** Optional overscan for a user-enabled exterior shadow. */
    static String applyWithEdgeGuard(String upstreamVertex) {
        String guarded = apply(upstreamVertex);
        guarded = replaceExactlyOnce(guarded, RASTER_SCALE,
                "vec2 rasterScale = (safeGlassSize + vec2(4.0 + 2.0 * u_edgeRasterGuardPx))"
                        + " / safeGlassSize;",
                "Prismal optional edge raster expansion");
        return replaceExactlyOnce(guarded, "uniform vec2 u_glassSize;",
                "uniform vec2 u_glassSize;\nuniform float u_edgeRasterGuardPx;",
                "Prismal optional edge raster uniform");
    }

    private static String replaceExactlyOnce(
            String source, String oldText, String newText, String label) {
        int first = source.indexOf(oldText);
        if (first < 0 || source.indexOf(oldText, first + oldText.length()) >= 0) {
            throw new IllegalStateException(label + " upstream contract changed");
        }
        return source.substring(0, first) + newText
                + source.substring(first + oldText.length());
    }
}
