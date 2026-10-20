package com.hellovoid.liquiddock;

/** Retain one desktop-folder glass snapshot across the launcher return fade. */
final class LauncherGlassFolderReturnState {
    private LauncherGlassGeometry.Snapshot geometry;
    private int rootWidth;
    private int rootHeight;

    void begin(LauncherGlassGeometry.Snapshot geometry, int rootWidth, int rootHeight) {
        if (geometry == null || rootWidth <= 0 || rootHeight <= 0) {
            clear();
            return;
        }
        this.geometry = geometry;
        this.rootWidth = rootWidth;
        this.rootHeight = rootHeight;
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

    boolean clear() {
        boolean wasActive = isActive();
        geometry = null;
        rootWidth = 0;
        rootHeight = 0;
        return wasActive;
    }
}
