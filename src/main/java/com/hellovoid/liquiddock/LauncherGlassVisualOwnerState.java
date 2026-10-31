package com.hellovoid.liquiddock;

/** Pure visual-owner state for one static Launcher node. */
final class LauncherGlassVisualOwnerState {
    private static final float EPSILON = 0.001f;
    private boolean launchProxyActive;
    private float[] launchProxyRect;
    private float launchProxyAlpha = 1f;

    /** Own the visual slot while MIUI's proxy exists but has not made its icon visible. */
    boolean holdLaunchProxyHidden() {
        boolean changed = !launchProxyActive || launchProxyRect != null
                || launchProxyAlpha != 0f;
        launchProxyActive = true;
        launchProxyRect = null;
        launchProxyAlpha = 0f;
        return changed;
    }

    /** The first valid vendor-visible final-consumer geometry frame publishes proxy geometry. */
    boolean updateLaunchProxyRect(float[] rect) {
        return updateLaunchProxyRect(rect, 1f);
    }

    /** The final consumer's rectangle and opacity are one visual-owner sample. */
    boolean updateLaunchProxyRect(float[] rect, float alpha) {
        if (!valid(rect)) return false;
        boolean geometryChanged = !same(launchProxyRect, rect);
        float nextAlpha = Float.isFinite(alpha) ? clamp(alpha) : 0f;
        boolean alphaChanged = Math.abs(launchProxyAlpha - nextAlpha) >= EPSILON;
        boolean changed = !launchProxyActive || geometryChanged || alphaChanged;
        if (!changed) return false;
        launchProxyActive = true;
        if (geometryChanged) launchProxyRect = rect.clone();
        // Keep the published comparison baseline until an alpha-only delta is large enough
        // to trigger redraw. Advancing it for every sub-EPSILON sample could suppress a
        // continuous fade forever when the proxy geometry itself remains stationary.
        launchProxyAlpha = nextAlpha;
        return true;
    }

    boolean endLaunchProxy() {
        if (!launchProxyActive && launchProxyRect == null && launchProxyAlpha == 1f) return false;
        launchProxyActive = false;
        launchProxyRect = null;
        launchProxyAlpha = 1f;
        return true;
    }

    boolean isLaunchProxyActive() { return launchProxyActive; }

    float alpha() { return launchProxyAlpha; }

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

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
