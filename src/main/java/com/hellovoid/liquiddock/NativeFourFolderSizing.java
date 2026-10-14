package com.hellovoid.liquiddock;

/** Geometry inputs for Launcher 4.50's native three-large/four-small preview recipe. */
final class NativeFourFolderSizing {
    static final class Size {
        final int insetX, insetY, backgroundWidth, backgroundHeight;
        final int edgeX, edgeY, gapX, gapY, largeWidth, largeHeight;
        final int smallGap, smallWidth, smallHeight;

        Size(int width, int height, int cellWidth, int cellHeight, int iconWidth, int iconHeight) {
            // The union of four adjacent icon bodies excludes half of the
            // single-cell blank space at each outside edge. Grid pitch comes
            // from the verified two-cell frame; no fixed pixel sizes.
            insetX = Math.max(0, cellWidth - iconWidth) / 2;
            insetY = Math.max(0, cellHeight - iconHeight) / 2;
            backgroundWidth = Math.max(1, width - insetX * 2);
            backgroundHeight = Math.max(1, height - insetY * 2);
            edgeX = (int) (backgroundWidth * .088f);
            gapX = (int) (backgroundWidth * .075f);
            gapY = (int) (backgroundHeight * .075f);
            largeWidth = (backgroundWidth - 2 * edgeX - gapX) / 2;
            int verticalEdge = (int) (backgroundHeight * .088f);
            largeHeight = (backgroundHeight - 2 * verticalEdge - gapY) / 2;
            edgeY = (backgroundHeight - 2 * largeHeight - gapY) / 2;
            smallGap = (int) (backgroundWidth * .025f);
            smallWidth = (largeWidth - smallGap) / 2;
            smallHeight = (largeHeight - smallGap) / 2;
        }
    }

    static Size calculate(int width, int height, int nativeCell, int iconSize) {
        return calculate(width, height, nativeCell, nativeCell, iconSize, iconSize);
    }

    static Size calculate(int width, int height, int cellWidth, int cellHeight,
            int iconWidth, int iconHeight) {
        return new Size(width, height, cellWidth, cellHeight, iconWidth, iconHeight);
    }

    /**
     * OEM LauncherIconImageView defaults to a 1x1 widget measurement whenever
     * its custom fields are zero. Keep the corrected size only on the real,
     * attached workspace folder, not the FolderAnimController's copy.
     */
    static boolean shouldPersistImageSize(boolean workspaceOwner, int width, int height) {
        return workspaceOwner && width > 0 && height > 0;
    }

    /** Parent 2x2 bounds may survive reparenting while a native child stays 1x1. */
    static boolean childNeedsLayout(int measuredW, int measuredH, int laidW, int laidH,
            int expectedW, int expectedH) {
        return measuredW != expectedW || measuredH != expectedH
                || laidW != expectedW || laidH != expectedH;
    }
}
