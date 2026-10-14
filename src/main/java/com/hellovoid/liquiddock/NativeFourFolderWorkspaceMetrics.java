package com.hellovoid.liquiddock;

import java.lang.ref.WeakReference;

/**
 * Immutable change signature for native folder icon geometry.
 * No strong reference to a Workspace/View/GridConfig may escape through
 * the WeakHashMap cache value.
 */
final class NativeFourFolderWorkspaceMetrics {
    private final WeakReference<Object> grid;
    private final int cellW, cellH, iconBase, iconScaled;
    private final int orientation, density, uiMode, direction;
    final int iconW, iconH;
    final boolean sampledSibling;

    NativeFourFolderWorkspaceMetrics(Object grid, int cellW, int cellH,
            int base, int scaled, int orientation, int density, int uiMode,
            int direction, int iconW, int iconH, boolean sampledSibling) {
        this.grid = new WeakReference<>(grid);
        this.cellW = cellW;
        this.cellH = cellH;
        this.iconBase = base;
        this.iconScaled = scaled;
        this.orientation = orientation;
        this.density = density;
        this.uiMode = uiMode;
        this.direction = direction;
        this.iconW = iconW;
        this.iconH = iconH;
        this.sampledSibling = sampledSibling;
    }

    boolean matches(Object grid, int cellW, int cellH, int base, int scaled,
            int orientation, int density, int uiMode, int direction) {
        return this.grid.get() == grid
                && this.cellW == cellW && this.cellH == cellH
                && iconBase == base && iconScaled == scaled
                && this.orientation == orientation && this.density == density
                && this.uiMode == uiMode && this.direction == direction;
    }
}
