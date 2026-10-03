package com.hellovoid.liquiddock;

/** Shared geometry for widget layouts on the custom CellLayout grid. */
final class WidgetGridSizing {
    private static volatile boolean widgetAdaptationEnabled;

    private WidgetGridSizing() {}

    static void setWidgetAdaptationEnabled(boolean enabled) {
        widgetAdaptationEnabled = enabled;
    }

    static boolean shouldAdaptWidgets(boolean gridEnabled, boolean adaptationEnabled) {
        return gridEnabled && adaptationEnabled;
    }

    static boolean isSupportedSpec(int spanX, int spanY) {
        // The active CellLayout dimensions are the real upper bound. Do not encode a second
        // launcher-specific whitelist here: any positive span that fits the current grid is valid.
        return spanX > 0 && spanY > 0;
    }

    /**
     * Returns {left, top, width, height} for the complete grid allocation.
     * When widget adaptation is disabled, the empty rectangle makes both the
     * setupLayoutParam and post-layout enforcement paths leave MIUI untouched.
     */
    static int[] gridRect(int cellX, int cellY, int spanX, int spanY,
                          int[] xs, int[] ys, int cellWidth, int cellHeight,
                          int widthGap, int heightGap) {
        if (!widgetAdaptationEnabled) return new int[]{0, 0, 0, 0};
        if (spanX <= 0 || spanY <= 0 || cellWidth <= 0 || cellHeight <= 0
                || xs == null || ys == null || xs.length == 0 || ys.length == 0
                || cellX < 0 || cellY < 0
                || cellX + spanX > xs.length || cellY + spanY > ys.length) {
            return new int[]{0, 0, 0, 0};
        }

        int left = xs[cellX];
        int right = xs[cellX + spanX - 1] + cellWidth;
        int top = ys[cellY];
        int bottom = ys[cellY + spanY - 1] + cellHeight;

        return new int[]{
                left,
                top,
                Math.max(0, right - left),
                Math.max(0, bottom - top)
        };
    }
}
