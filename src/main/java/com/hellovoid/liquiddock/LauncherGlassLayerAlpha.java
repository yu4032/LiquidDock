package com.hellovoid.liquiddock;

/** Independent scene, unlock and native Workspace opacity; none opens a hidden scene. */
final class LauncherGlassLayerAlpha {
    private float sceneAlpha;
    private float motionAlpha = 1f;
    private float workspaceAlpha = 1f;

    float sceneAlpha() { return sceneAlpha; }
    float alpha() { return sceneAlpha * motionAlpha * workspaceAlpha; }
    void setSceneAlpha(float alpha) { sceneAlpha = safeAlpha(alpha); }
    void setMotionAlpha(float alpha) { motionAlpha = safeAlpha(alpha); }

    void setWorkspaceAlpha(float alpha, boolean launchProxyActive) {
        // FloatingIconView2 owns the icon outside Workspace while the source is hidden.
        // Keep its existing presentation; its final-consumer geometry owns visibility.
        workspaceAlpha = launchProxyActive ? 1f : safeAlpha(alpha);
    }

    boolean startMotionReveal(long durationMs) {
        motionAlpha = durationMs > 0L ? 0f : 1f;
        return durationMs > 0L;
    }

    private static float safeAlpha(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
    }
}
