package com.hellovoid.liquiddock;

/** One-shot freshness gate: one authoritative backdrop is consumed per visible Searchbox cycle. */
final class MiuiSearchboxSnapshotState {
    private boolean capturePending;
    private int rootWidth;
    private int rootHeight;

    /** Only changes to root dimensions invalidate the frozen backdrop. */
    /**
     * Recreate the input producer when the root's orientation changes.
     * Resizes caused by insets/keyboard in the same orientation do not need rebinding.
     */
    static boolean orientationChanged(int oldWidth, int oldHeight, int width, int height) {
        return oldWidth > 0 && oldHeight > 0 && width > 0 && height > 0
                && (oldWidth > oldHeight) != (width > height);
    }

    synchronized boolean updateRootSize(int width, int height) {
        if (width <= 0 || height <= 0) return false;
        boolean changed = rootWidth > 0 && rootHeight > 0
                && (rootWidth != width || rootHeight != height);
        rootWidth = width;
        rootHeight = height;
        return changed;
    }

    synchronized void beginCapture() {
        capturePending = true;
    }

    synchronized boolean acceptFreshFrame() {
        return acceptFreshFrame(rootWidth, rootHeight);
    }

    /** A frame for the previous orientation must not consume the pending capture. */
    synchronized boolean acceptFreshFrame(int width, int height) {
        if (!capturePending || (rootWidth > 0 && rootHeight > 0
                && (rootWidth != width || rootHeight != height))) return false;
        capturePending = false;
        return true;
    }
}
