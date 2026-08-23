package com.hellovoid.liquiddock;

/** Mirrors Launcher 4.50's own final FloatingIcon consumer visibility rules. */
final class LauncherGlassProxyVisibility {
    private LauncherGlassProxyVisibility() {}

    /** FloatingIconView2.setAlpha(f > 0.1f ? 1 : 0). */
    static boolean isView2Visible(float alpha, boolean drawIcon) {
        return drawIcon && Float.isFinite(alpha) && alpha > 0.1f;
    }

    /** FloatingIconLayer2 SurfaceControl alpha is visible only for f > 0. */
    static boolean isLayer2Visible(float alpha, boolean drawIcon) {
        return drawIcon && Float.isFinite(alpha) && alpha > 0f;
    }
}
