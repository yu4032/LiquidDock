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
    final Orientation landscape;
    final Orientation portrait;
    final float density;

    HomeGridInstallConfig(
            boolean enabled,
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
        this.enabled = enabled;
        this.landscape = new Orientation(
                landscapeLeft, landscapeRight, landscapeTop, landscapeBottom,
                landscapeRowGap, landscapeIndicatorY);
        this.portrait = new Orientation(
                portraitLeft, portraitRight, portraitTop, portraitBottom,
                portraitRowGap, portraitIndicatorY);
        this.density = density;
    }

    Orientation orientation(boolean portraitMode) {
        return portraitMode ? portrait : landscape;
    }
}
