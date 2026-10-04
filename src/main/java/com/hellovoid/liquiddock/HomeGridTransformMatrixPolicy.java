package com.hellovoid.liquiddock;

/**
 * Matrix helpers for MIUI LayoutTransformRule.
 *
 * <p>The destination matrix is initialized by MIUI with non-null SPACE_INFO sentinels. Empty
 * target cells must retain those sentinels because transformToHVArray() dereferences every cell
 * without a null check.</p>
 */
final class HomeGridTransformMatrixPolicy {
    private HomeGridTransformMatrixPolicy() {}

    static Object[][] copyTemplate(Object[][] source, int columns, int rows) {
        if (source == null || columns <= 0 || rows <= 0 || source.length != columns) {
            throw new IllegalArgumentException("invalid transform template");
        }
        Object[][] copy = new Object[columns][rows];
        for (int x = 0; x < columns; x++) {
            if (source[x] == null || source[x].length != rows) {
                throw new IllegalArgumentException("ragged transform template");
            }
            System.arraycopy(source[x], 0, copy[x], 0, rows);
        }
        return copy;
    }
}
