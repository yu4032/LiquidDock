package com.hellovoid.liquiddock;

/** Runtime geometry gate for the Gboard companion toolbar in either orientation. */
final class GboardHandwritingToolbarGeometryPolicy {
    private GboardHandwritingToolbarGeometryPolicy() {}

    static boolean isToolbar(float widthPx, float heightPx, float density) {
        if (!finite(widthPx) || !finite(heightPx) || !finite(density)
                || widthPx <= 0f || heightPx <= 0f || density <= 0f) return false;

        float shortSideDp = Math.min(widthPx, heightPx) / density;
        float longSideDp = Math.max(widthPx, heightPx) / density;
        if (shortSideDp < 28f || shortSideDp > 180f) return false;
        if (longSideDp < 64f || longSideDp > 900f) return false;

        // The same WidgetSoftKeyboardView animates between horizontal and vertical toolbar
        // layouts. Treat orientation as presentation only; the short side is toolbar thickness.
        return longSideDp >= shortSideDp * 1.20f;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
