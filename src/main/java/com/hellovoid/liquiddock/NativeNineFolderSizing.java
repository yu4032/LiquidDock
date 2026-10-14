package com.hellovoid.liquiddock;

/** Independent three-by-three preview metrics inside the native background bounds. */
final class NativeNineFolderSizing {
    private NativeNineFolderSizing() {}

    static final class Geometry {
        final int edgeX, edgeY, gapX, gapY, iconWidth, iconHeight;
        Geometry(int width, int height) {
            edgeX = Math.max(0, (int) (width * .088f));
            edgeY = Math.max(0, (int) (height * .088f));
            gapX = Math.max(0, (int) (width * .075f));
            gapY = Math.max(0, (int) (height * .075f));
            iconWidth = Math.max(1, (width - 2 * edgeX - 2 * gapX) / 3);
            iconHeight = Math.max(1, (height - 2 * edgeY - 2 * gapY) / 3);
        }
    }

    static Geometry calculate(int width, int height) {
        return new Geometry(Math.max(1, width), Math.max(1, height));
    }
}
