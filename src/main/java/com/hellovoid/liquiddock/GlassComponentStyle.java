package com.hellovoid.liquiddock;

/** Immutable per-component Launcher/Dock glass style. */
final class GlassComponentStyle {
    final boolean enabled;
    final float sizeOffsetDp;
    final float cornerRadiusDp;

    GlassComponentStyle(boolean enabled, float sizeOffsetDp, float cornerRadiusDp) {
        this.enabled = enabled;
        this.sizeOffsetDp = Float.isFinite(sizeOffsetDp) ? sizeOffsetDp : 0f;
        this.cornerRadiusDp = Float.isFinite(cornerRadiusDp)
                ? Math.max(0f, cornerRadiusDp) : 0f;
    }

    boolean autoCornerRadius() { return cornerRadiusDp <= 0f; }
}
