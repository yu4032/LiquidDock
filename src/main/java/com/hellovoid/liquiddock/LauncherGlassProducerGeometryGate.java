package com.hellovoid.liquiddock;

/** Pure producer/root coherence gate used before publishing a Launcher glass generation. */
final class LauncherGlassProducerGeometryGate {
    private LauncherGlassProducerGeometryGate() {}

    static boolean matchesRoot(
            int rootWidth, int rootHeight,
            int surfaceWidth, int surfaceHeight,
            int insetLeft, int insetTop, int insetRight, int insetBottom) {
        if (rootWidth <= 0 || rootHeight <= 0 || surfaceWidth <= 0 || surfaceHeight <= 0) {
            return false;
        }
        int left = Math.max(0, insetLeft);
        int top = Math.max(0, insetTop);
        int right = Math.max(0, insetRight);
        int bottom = Math.max(0, insetBottom);
        int contentWidth = surfaceWidth - left - right;
        int contentHeight = surfaceHeight - top - bottom;
        return contentWidth == rootWidth && contentHeight == rootHeight;
    }
}
