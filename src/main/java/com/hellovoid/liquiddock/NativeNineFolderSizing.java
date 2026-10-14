package com.hellovoid.liquiddock;

/**
 * Geometry of the stock Launcher 4.50 FolderIconPreviewContainer2X2_9:
 * eight 3x3-sized icons and four half-sized icons in the ninth cell.
 *
 * Mirrors the OEM preMeasure2x2 dimensions, using our actual background
 * dimensions rather than the stock one-cell widget size.
 */
final class NativeNineFolderSizing {
    private NativeNineFolderSizing() {}

    static final class Geometry {
        final int edgeX, edgeY, gapX, gapY;
        final int largeWidth, largeHeight;
        final int smallGap, smallWidth, smallHeight;

        Geometry(int edgeX, int edgeY, int gap, int large, int smallGap, int small) {
            this.edgeX = edgeX;
            this.edgeY = edgeY;
            this.gapX = gap;
            this.gapY = gap;
            this.largeWidth = large;
            this.largeHeight = large;
            this.smallGap = smallGap;
            this.smallWidth = small;
            this.smallHeight = small;
        }
    }

    static Geometry calculate(int width, int height, boolean turner) {
        if (width <= 0 || height <= 0) return null;
        int side = Math.min(width, height);
        int edge = (int) (side * (turner ? .09f : .0825f));
        int gap = (int) (side * .044f);
        int large = (side - 2 * edge - 2 * gap) / 3;
        int smallGap = (int) (side * .022f);
        int small = (large - smallGap) / 2;
        if (large <= 0 || small <= 0) return null;
        int edgeX = width <= height ? edge : (width - large * 3 - gap * 2) / 2;
        int edgeY = width <= height ? (height - large * 3 - gap * 2) / 2 : edge;
        return new Geometry(edgeX, edgeY, gap, large, smallGap, small);
    }
}
