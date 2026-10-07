package com.hellovoid.liquiddock;

/** Independent presentation and motion opacity; motion never opens a hidden scene. */
final class LauncherGlassLayerAlpha {
    private float sceneAlpha;
    private float motionAlpha = 1f;

    float sceneAlpha() { return sceneAlpha; }
    float alpha() { return sceneAlpha * motionAlpha; }
    void setSceneAlpha(float alpha) { sceneAlpha = safeAlpha(alpha); }
    void setMotionAlpha(float alpha) { motionAlpha = safeAlpha(alpha); }

    boolean startMotionReveal(long durationMs) {
        motionAlpha = durationMs > 0L ? 0f : 1f;
        return durationMs > 0L;
    }

    private static float safeAlpha(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
    }
}
