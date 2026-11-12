package com.hellovoid.liquiddock;

/** Owns only an active caption menu's compositor radius and restores the OEM value. */
final class HandleMenuSurfaceCornerLease {
    interface Writer { boolean setRadius(float radiusPx); }

    private final float nativeRadiusPx;
    private final Writer writer;
    private boolean overridden;
    private float appliedRadiusPx;

    HandleMenuSurfaceCornerLease(float nativeRadiusPx, Writer writer) {
        this.nativeRadiusPx = nativeRadiusPx;
        this.writer = writer;
    }

    boolean update(float requestedRadiusPx) {
        if (!Float.isFinite(requestedRadiusPx) || requestedRadiusPx < 0) return restore();
        if (overridden && Float.compare(appliedRadiusPx, requestedRadiusPx) == 0) return true;
        if (!writer.setRadius(requestedRadiusPx)) return false;
        appliedRadiusPx = requestedRadiusPx;
        overridden = true;
        return true;
    }

    boolean restore() {
        if (!overridden) return true;
        if (!writer.setRadius(nativeRadiusPx)) return false;
        overridden = false;
        return true;
    }

    // MiuiDecorationDot.addWindow sets the container radius from its actual window height.
    static float nativeRadius(float height, boolean extendedMode) {
        return height / (extendedMode ? 4f : 2f) + 1f;
    }
}
