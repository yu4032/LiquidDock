package com.hellovoid.liquiddock;

/** Pure bounds policy for free-form custom home-grid placement. */
final class HomeGridDropLegalityPolicy {
    private HomeGridDropLegalityPolicy() {}

    static boolean isLegal(int columns, int rows,
                           int cellX, int cellY,
                           int spanX, int spanY) {
        if (columns <= 0 || rows <= 0 || spanX <= 0 || spanY <= 0
                || cellX < 0 || cellY < 0) {
            return false;
        }
        long right = (long) cellX + spanX;
        long bottom = (long) cellY + spanY;
        return right <= columns && bottom <= rows;
    }
}
