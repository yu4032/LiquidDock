package com.hellovoid.liquiddock;

/** Pure bounds policy for free-form custom home-grid placement. */
final class HomeGridDropLegalityPolicy {
    private HomeGridDropLegalityPolicy() {}

    static boolean isLegal(HomeGridProfile ignoredProfile,
                           int columns, int rows,
                           int cellX, int cellY,
                           int spanX, int spanY) {
        return isLegal(columns, rows, cellX, cellY, spanX, spanY);
    }

    static boolean isLegal(int columns, int rows,
                           int cellX, int cellY,
                           int spanX, int spanY) {
        if (columns <= 0 || rows <= 0 || spanX <= 0 || spanY <= 0
                || cellX < 0 || cellY < 0) {
            return false;
        }
        long right = (long) cellX + spanX;
        long bottom = (long) cellY + spanY;
        if (right > columns || bottom > rows) return false;

        // Rotation swaps rows/columns but keeps widget spanX/spanY unchanged.
        // Refuse placements that could never fit after a physical rotation.
        return spanX <= rows && spanY <= columns;
    }
}
