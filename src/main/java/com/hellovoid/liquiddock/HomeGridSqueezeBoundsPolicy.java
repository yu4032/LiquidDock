package com.hellovoid.liquiddock;

/** Pure rectangular bounds policy shared by free-grid squeeze guards. */
final class HomeGridSqueezeBoundsPolicy {
    private HomeGridSqueezeBoundsPolicy() {}

    static boolean fits(
            int columns, int rows,
            int cellX, int cellY,
            int spanX, int spanY) {
        return columns > 0
                && rows > 0
                && cellX >= 0
                && cellY >= 0
                && spanX > 0
                && spanY > 0
                && (long) cellX + spanX <= columns
                && (long) cellY + spanY <= rows;
    }
}
