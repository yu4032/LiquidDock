package com.hellovoid.liquiddock;

/** Retain one desktop-folder glass snapshot across the launcher return fade. */
final class LauncherGlassFolderReturnState {
    private LauncherGlassGeometry.Snapshot geometry;
    private int rootWidth;
    private int rootHeight;
    private boolean followsNativeClose;
    private float alpha = 1f;

    void begin(LauncherGlassGeometry.Snapshot geometry, int rootWidth, int rootHeight,
               boolean followsNativeClose) {
        if (geometry == null || rootWidth <= 0 || rootHeight <= 0) {
            clear();
            return;
        }
        this.geometry = geometry;
        this.rootWidth = rootWidth;
        this.rootHeight = rootHeight;
        this.followsNativeClose = followsNativeClose;
        alpha = followsNativeClose ? 0f : 1f;
    }

    boolean updateCloseProgress(float openProgress) {
        if (!isActive() || !followsNativeClose || !Float.isFinite(openProgress)) return false;
        float next = LauncherGlassReturnFade.alpha(1f - Math.max(0f,
                Math.min(1f, openProgress)));
        if (Math.abs(alpha - next) < 0.001f) return false;
        alpha = next;
        return true;
    }

    LauncherGlassGeometry.Snapshot geometryForRoot(int rootWidth, int rootHeight) {
        if (!isActive()) return null;
        if (this.rootWidth != rootWidth || this.rootHeight != rootHeight) {
            clear();
            return null;
        }
        return geometry;
    }

    boolean isActive() {
        return geometry != null;
    }

    float alpha() { return alpha; }

    boolean clear() {
        boolean wasActive = isActive();
        geometry = null;
        rootWidth = 0;
        rootHeight = 0;
        followsNativeClose = false;
        alpha = 1f;
        return wasActive;
    }
}
