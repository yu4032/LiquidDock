package com.hellovoid.liquiddock;

/** Scales OEM preview icons in place without changing their native cell centers. */
final class NativeFolderPreviewScale {
    static final int DEFAULT_PERCENT = 100;
    static final int MIN_PERCENT = 60;
    static final int MAX_PERCENT = 110;

    static final class Geometry {
        final int edgeX, edgeY, gapX, gapY;
        final int largeWidth, largeHeight, smallGap, smallWidth, smallHeight;

        Geometry(int edgeX, int edgeY, int gapX, int gapY, int largeWidth,
                int largeHeight, int smallGap, int smallWidth, int smallHeight) {
            this.edgeX = edgeX;
            this.edgeY = edgeY;
            this.gapX = gapX;
            this.gapY = gapY;
            this.largeWidth = largeWidth;
            this.largeHeight = largeHeight;
            this.smallGap = smallGap;
            this.smallWidth = smallWidth;
            this.smallHeight = smallHeight;
        }
    }

    private NativeFolderPreviewScale() {}

    static Geometry four(NativeFourFolderSizing.Size size, int percent) {
        return scale(size.edgeX, size.edgeY, size.gapX, size.gapY,
                size.largeWidth, size.largeHeight, size.smallGap,
                size.smallWidth, size.smallHeight, percent);
    }

    static Geometry nine(NativeNineFolderSizing.Geometry size, int percent) {
        return scale(size.edgeX, size.edgeY, size.gapX, size.gapY,
                size.largeWidth, size.largeHeight, size.smallGap,
                size.smallWidth, size.smallHeight, percent);
    }

    private static Geometry scale(int edgeX, int edgeY, int gapX, int gapY,
            int largeWidth, int largeHeight, int smallGap, int smallWidth,
            int smallHeight, int percent) {
        int bounded = Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, percent));
        if (bounded == DEFAULT_PERCENT) {
            return new Geometry(edgeX, edgeY, gapX, gapY, largeWidth, largeHeight,
                    smallGap, smallWidth, smallHeight);
        }
        float ratio = bounded / 100f;
        int w = Math.max(1, Math.round(largeWidth * ratio));
        int h = Math.max(1, Math.round(largeHeight * ratio));
        int dx = largeWidth - w;
        int dy = largeHeight - h;
        // Preserve all native 2-column / 3-column cell centers. Increasing
        // inter-icon spacing compensates for smaller icons (and vice versa).
        int newEdgeX = edgeX + Math.round(dx / 2f);
        int newEdgeY = edgeY + Math.round(dy / 2f);
        int newGapX = gapX + dx;
        int newGapY = gapY + dy;
        int newSmallGap = Math.max(0, Math.round(smallGap * ratio));
        int newSmallWidth = Math.max(1, (w - newSmallGap) / 2);
        int newSmallHeight = Math.max(1, (h - newSmallGap) / 2);
        return new Geometry(newEdgeX, newEdgeY, newGapX, newGapY,
                w, h, newSmallGap, newSmallWidth, newSmallHeight);
    }
}
