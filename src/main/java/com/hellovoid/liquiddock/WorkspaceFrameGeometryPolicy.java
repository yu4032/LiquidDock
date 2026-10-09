package com.hellovoid.liquiddock;

/**
 * Reject a root-sized PassBlur frame if the captured logical viewport no longer matches
 * the Launcher root or its full-root output surface. A stale geometry generation must
 * never be stretched across the newer window and presented as enlarged glass.
 */
final class WorkspaceFrameGeometryPolicy {
    private WorkspaceFrameGeometryPolicy() {}

    static String mismatch(int frameWidth, int frameHeight, int frameRotation,
                           int rootWidth, int rootHeight, int rootRotation,
                           int outputWidth, int outputHeight) {
        if (frameWidth <= 0 || frameHeight <= 0
                || rootWidth <= 0 || rootHeight <= 0) return "invalid-geometry";
        if (frameWidth != rootWidth || frameHeight != rootHeight) {
            return "root-size";
        }
        if (frameRotation != rootRotation) return "rotation";
        if (outputWidth > 0 && outputHeight > 0
                && (outputWidth != rootWidth || outputHeight != rootHeight)) {
            return "window-size";
        }
        return null;
    }
}
