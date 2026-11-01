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
        // The root glass is independent of Workspace. With icon glass off there is no launch
        // proxy to own the app-opening transition, so don't let Workspace's launch fade hide it.
        workspaceAlpha = launchProxyActive || !GlassRuntimeState.isIconEnabled()
                ? 1f : safeAlpha(alpha);
    }

    boolean startMotionReveal(long durationMs) {
        motionAlpha = durationMs > 0L ? 0f : 1f;
        return durationMs > 0L;
    }

    private static float safeAlpha(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
    }
}
