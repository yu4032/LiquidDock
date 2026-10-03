package com.hellovoid.liquiddock;

/** Immutable free-form HOME grid dimensions shared by all runtime layout policies. */
final class HomeGridDimensions {
    final int landscapeColumns;
    final int landscapeRows;

    HomeGridDimensions(int landscapeColumns, int landscapeRows) {
        this.landscapeColumns = Math.max(2, Math.min(10, landscapeColumns));
        this.landscapeRows = Math.max(2, Math.min(6, landscapeRows));
    }

    int columns(HomeGridOrientation orientation) {
        return orientation == HomeGridOrientation.PORTRAIT
                ? landscapeRows : landscapeColumns;
    }

    int rows(HomeGridOrientation orientation) {
        return orientation == HomeGridOrientation.PORTRAIT
                ? landscapeColumns : landscapeRows;
    }

    String key() {
        return landscapeColumns + "x" + landscapeRows;
    }

    boolean sameAs(HomeGridDimensions other) {
        return other != null
                && landscapeColumns == other.landscapeColumns
                && landscapeRows == other.landscapeRows;
    }
}
