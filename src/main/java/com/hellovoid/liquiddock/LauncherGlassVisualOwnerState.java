package com.hellovoid.liquiddock;

/** Pure visual-owner state for one static Launcher node. */
final class LauncherGlassVisualOwnerState {
    private static final float EPSILON = 0.001f;
    private boolean launchProxyActive;
    private float[] launchProxyRect;
    private float launchProxyAlpha = 1f;
    private boolean launchProxyReturning;
    private float launchProxyReturnProgress;

    /** Own the visual slot while MIUI's proxy exists but has not made its icon visible. */
    boolean holdLaunchProxyHidden() {
        return holdLaunchProxyHidden(false, 0f);
    }

    boolean holdLaunchProxyHidden(boolean returning, float progress) {
        float nextProgress = clamp(progress);
        boolean changed = !launchProxyActive || launchProxyRect != null
                || launchProxyAlpha != 0f || launchProxyReturning != returning
                || Math.abs(launchProxyReturnProgress - nextProgress) >= EPSILON;
        launchProxyActive = true;
        launchProxyRect = null;
        launchProxyAlpha = 0f;
        launchProxyReturning = returning;
        launchProxyReturnProgress = nextProgress;
        return changed;
    }

    /** The first valid vendor-visible final-consumer geometry frame publishes proxy geometry. */
    boolean updateLaunchProxyRect(float[] rect) {
        return updateLaunchProxyRect(rect, 1f);
    }

    /** The final consumer's rectangle and opacity are one visual-owner sample. */
    boolean updateLaunchProxyRect(float[] rect, float alpha) {
        return updateLaunchProxyRect(rect, alpha, false, 0f);
    }

    boolean updateLaunchProxyRect(
            float[] rect, float alpha, boolean returning, float progress) {
        if (!valid(rect)) return false;
        boolean geometryChanged = !same(launchProxyRect, rect);
        float nextAlpha = Float.isFinite(alpha) ? clamp(alpha) : 0f;
        float nextProgress = clamp(progress);
        boolean alphaChanged = Math.abs(launchProxyAlpha - nextAlpha) >= EPSILON;
        boolean transitionChanged = launchProxyReturning != returning
                || Math.abs(launchProxyReturnProgress - nextProgress) >= EPSILON;
        boolean changed = !launchProxyActive || geometryChanged || alphaChanged || transitionChanged;
        if (!changed) return false;
        launchProxyActive = true;
        if (geometryChanged) launchProxyRect = rect.clone();
        // Keep the published comparison baseline until an alpha-only delta is large enough
        // to trigger redraw. Advancing it for every sub-EPSILON sample could suppress a
        // continuous fade forever when the proxy geometry itself remains stationary.
        launchProxyAlpha = nextAlpha;
        launchProxyReturning = returning;
        launchProxyReturnProgress = nextProgress;
        return true;
    }

    boolean matchesLaunchProxyVisual(float[] rect, float alpha) {
        if (!launchProxyActive) return false;
        boolean rectMatches = launchProxyRect == null
                ? rect == null : same(launchProxyRect, rect);
        float nextAlpha = Float.isFinite(alpha) ? clamp(alpha) : 0f;
        return rectMatches && Math.abs(launchProxyAlpha - nextAlpha) < EPSILON;
    }

    boolean endLaunchProxy() {
        if (!launchProxyActive && launchProxyRect == null && launchProxyAlpha == 1f) return false;
        launchProxyActive = false;
        launchProxyRect = null;
        launchProxyAlpha = 1f;
        launchProxyReturning = false;
        launchProxyReturnProgress = 0f;
        return true;
    }

    boolean isLaunchProxyActive() { return launchProxyActive; }
    boolean isLaunchProxyReturning() { return launchProxyReturning; }
    float launchProxyReturnProgress() { return launchProxyReturnProgress; }

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
