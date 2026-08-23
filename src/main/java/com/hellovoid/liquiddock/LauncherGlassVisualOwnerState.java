package com.hellovoid.liquiddock;

/** Pure visual-owner state for one static Launcher node. */
final class LauncherGlassVisualOwnerState {
    private static final float EPSILON = 0.001f;
    private boolean launchProxyActive;
    private float[] launchProxyRect;

    /** Own the visual slot while MIUI's proxy exists but has not made its icon visible. */
    boolean holdLaunchProxyHidden() {
        boolean changed = !launchProxyActive || launchProxyRect != null;
        launchProxyActive = true;
        launchProxyRect = null;
        return changed;
    }

    /** The first valid vendor-visible final-consumer geometry frame publishes proxy geometry. */
    boolean updateLaunchProxyRect(float[] rect) {
        if (!valid(rect)) return false;
        boolean changed = !launchProxyActive || !same(launchProxyRect, rect);
        launchProxyActive = true;
        if (!same(launchProxyRect, rect)) launchProxyRect = rect.clone();
        return changed;
    }

    boolean endLaunchProxy() {
        if (!launchProxyActive && launchProxyRect == null) return false;
        launchProxyActive = false;
        launchProxyRect = null;
        return true;
    }

    boolean isLaunchProxyActive() { return launchProxyActive; }

    float[] copyLaunchProxyRect() {
        return launchProxyRect != null ? launchProxyRect.clone() : null;
    }

    private static boolean valid(float[] rect) {
        return rect != null && rect.length == 4
                && Float.isFinite(rect[0]) && Float.isFinite(rect[1])
                && Float.isFinite(rect[2]) && Float.isFinite(rect[3])
                && rect[2] > rect[0] && rect[3] > rect[1];
    }

    private static boolean same(float[] first, float[] second) {
        if (first == second) return true;
        if (!valid(first) || !valid(second)) return false;
        for (int i = 0; i < 4; i++) {
            if (Math.abs(first[i] - second[i]) >= EPSILON) return false;
        }
        return true;
    }
}
