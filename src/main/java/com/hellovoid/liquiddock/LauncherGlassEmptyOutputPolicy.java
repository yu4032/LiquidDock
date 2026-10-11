package com.hellovoid.liquiddock;

/**
 * A completely empty static glass layer has identical transparent pixels across source frames.
 *
 * <p>One successful transparent EGL present is required after the last drawable glass node
 * disappears. Further empty frames need no buffer swap until geometry returns or the Surface
 * is recreated. This state never suppresses a frame containing an actual drawable node.</p>
 */
final class LauncherGlassEmptyOutputPolicy {
    private boolean transparentFramePresented;

    /** Only called when no glass geometry exists in the current snapshot. */
    boolean canSkipEmptyFrame() {
        return transparentFramePresented;
    }

    /** Call only after EGL successfully presents the already-cleared static output. */
    void onSwapCompleted(boolean drewGeometry) {
        transparentFramePresented = !drewGeometry;
    }

    /** Rebound, resized and newly attached Surfaces must each get a fresh transparent buffer. */
    void onSurfaceReset() {
        transparentFramePresented = false;
    }
}
