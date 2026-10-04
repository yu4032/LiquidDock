package com.hellovoid.liquiddock;

/** Immutable, already-normalized runtime values for custom HOME grid installation. */
final class HomeGridInstallConfig {
    static final class Orientation {
        final int left;
        final int right;
        final int top;
        final int bottom;
        final int rowGap;
        final int indicatorY;

        Orientation(int left, int right, int top, int bottom, int rowGap, int indicatorY) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.rowGap = rowGap;
            this.indicatorY = indicatorY;
        }
    }

    final boolean enabled;
    final int columns;
    final int rows;
    final Orientation landscape;
    final Orientation portrait;
    final int splitHorizontalOffset;
    final float density;
    final boolean widgetHorizontalStretch;
    final boolean widgetPlacementGuard;

    HomeGridInstallConfig(
            boolean enabled,
            int columns,
            int rows,
            int landscapeLeft,
            int landscapeRight,
            int landscapeTop,
            int landscapeBottom,
            int portraitLeft,
            int portraitRight,
            int portraitTop,
            int portraitBottom,
            int landscapeRowGap,
            int portraitRowGap,
            int landscapeIndicatorY,
            int portraitIndicatorY,
            float density) {
        this(enabled, columns, rows,
                landscapeLeft, landscapeRight, landscapeTop, landscapeBottom,
                portraitLeft, portraitRight, portraitTop, portraitBottom,
                landscapeRowGap, portraitRowGap,
                landscapeIndicatorY, portraitIndicatorY,
                0, density);
    }

    HomeGridInstallConfig(
            boolean enabled,
            int columns,
            int rows,
            int landscapeLeft,
            int landscapeRight,
            int landscapeTop,
            int landscapeBottom,
            int portraitLeft,
            int portraitRight,
            int portraitTop,
            int portraitBottom,
            int landscapeRowGap,
            int portraitRowGap,
            int landscapeIndicatorY,
            int portraitIndicatorY,
            int splitHorizontalOffset,
            float density) {
        this(enabled, columns, rows,
                landscapeLeft, landscapeRight, landscapeTop, landscapeBottom,
                portraitLeft, portraitRight, portraitTop, portraitBottom,
                landscapeRowGap, portraitRowGap,
                landscapeIndicatorY, portraitIndicatorY,
                splitHorizontalOffset, density, false, true);
    }

    HomeGridInstallConfig(
            boolean enabled,
            int columns,
            int rows,
            int landscapeLeft,
            int landscapeRight,
            int landscapeTop,
            int landscapeBottom,
            int portraitLeft,
            int portraitRight,
            int portraitTop,
            int portraitBottom,
            int landscapeRowGap,
            int portraitRowGap,
            int landscapeIndicatorY,
            int portraitIndicatorY,
            int splitHorizontalOffset,
            float density,
            boolean widgetHorizontalStretch) {
        this(enabled, columns, rows,
                landscapeLeft, landscapeRight, landscapeTop, landscapeBottom,
                portraitLeft, portraitRight, portraitTop, portraitBottom,
                landscapeRowGap, portraitRowGap,
                landscapeIndicatorY, portraitIndicatorY,
                splitHorizontalOffset, density, widgetHorizontalStretch, true);
    }

    HomeGridInstallConfig(
            boolean enabled,
            int columns,
            int rows,
            int landscapeLeft,
            int landscapeRight,
            int landscapeTop,
            int landscapeBottom,
            int portraitLeft,
            int portraitRight,
            int portraitTop,
            int portraitBottom,
            int landscapeRowGap,
            int portraitRowGap,
            int landscapeIndicatorY,
            int portraitIndicatorY,
            int splitHorizontalOffset,
            float density,
            boolean widgetHorizontalStretch,
            boolean widgetPlacementGuard) {
        this.enabled = enabled;
        this.columns = Math.max(2, Math.min(10, columns));
        this.rows = Math.max(2, Math.min(6, rows));
        this.landscape = new Orientation(
                landscapeLeft, landscapeRight, landscapeTop, landscapeBottom,
                landscapeRowGap, landscapeIndicatorY);
        this.portrait = new Orientation(
                portraitLeft, portraitRight, portraitTop, portraitBottom,
                portraitRowGap, portraitIndicatorY);
        this.splitHorizontalOffset = splitHorizontalOffset;
        this.density = density;
        this.widgetHorizontalStretch = widgetHorizontalStretch;
        this.widgetPlacementGuard = widgetPlacementGuard;
    }

    Orientation orientation(boolean portraitMode) {
        return portraitMode ? portrait : landscape;
    }

    int countX(boolean portraitMode) {
        return portraitMode ? rows : columns;
    }

    int countY(boolean portraitMode) {
        return portraitMode ? columns : rows;
    }

    HomeGridInstallConfig withZeroHorizontalOffsets() {
        return new HomeGridInstallConfig(
                enabled,
                columns,
                rows,
                0,
                0,
                landscape.top,
                landscape.bottom,
                0,
                0,
                portrait.top,
                portrait.bottom,
                landscape.rowGap,
                portrait.rowGap,
                landscape.indicatorY,
                portrait.indicatorY,
                splitHorizontalOffset,
                density,
                widgetHorizontalStretch,
                widgetPlacementGuard);
    }

    boolean matchesGrid(int x, int y) {
        return (x == columns && y == rows) || (x == rows && y == columns);
    }
}
