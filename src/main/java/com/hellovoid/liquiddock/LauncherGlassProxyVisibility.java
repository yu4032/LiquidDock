package com.hellovoid.liquiddock;

/** Mirrors Launcher 4.50's FloatingIcon visibility and geometry-publication contracts. */
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

    /**
     * CLOSE_TO_HOME has a real hidden-proxy ownership phase: while the vendor proxy exists but
     * does not draw the icon, publishing its task-sized morph rectangle would render glass that
     * MIUI itself is not showing. Launch-away is different: the morph rectangle is already the
     * moving geometry authority from the first update, even before the proxy icon becomes visible.
     */
    static boolean shouldPublishGeometry(boolean closeToHome, boolean proxyVisible) {
        return !closeToHome || proxyVisible;
    }
}
