package com.hellovoid.liquiddock;

/** Android-free press interaction state shared by static and sink launcher glass nodes. */
final class LauncherGlassPressState {
    private static final float EPSILON = 0.001f;

    static final class Decision {
        final boolean animate;
        final boolean publishImmediately;
        final float startProgress;
        final float targetProgress;

        Decision(boolean animate, boolean publishImmediately, float startProgress, float targetProgress) {
            this.animate = animate;
            this.publishImmediately = publishImmediately;
            this.startProgress = startProgress;
            this.targetProgress = targetProgress;
        }
    }

    private boolean pressedTarget;
    private float progress;
    private float glowCenterX = 0.5f;
    private float glowCenterY = 0.5f;

    Decision setPressed(boolean pressed, float normalizedX, float normalizedY) {
        float nextX = clampCenter(normalizedX);
        float nextY = clampCenter(normalizedY);
        boolean centerChanged = glowCenterX != nextX || glowCenterY != nextY;
        glowCenterX = nextX;
        glowCenterY = nextY;

        float target = pressed ? 1f : 0f;
        if (pressedTarget != pressed) {
            pressedTarget = pressed;
            boolean animate = Math.abs(progress - target) >= EPSILON;
            if (!animate) progress = target;
            return new Decision(animate, !animate, progress, target);
        }
        return new Decision(false, centerChanged, progress, target);
    }

    Decision reset(boolean animated) {
        pressedTarget = false;
        if (animated && progress > EPSILON) {
            return new Decision(true, false, progress, 0f);
        }
        progress = 0f;
        glowCenterX = 0.5f;
        glowCenterY = 0.5f;
        return new Decision(false, true, 0f, 0f);
    }

    void setProgress(float value) {
        progress = clamp01(value);
    }

    boolean isPressedTarget() { return pressedTarget; }
    float progress() { return progress; }
    float glowCenterX() { return glowCenterX; }
    float glowCenterY() { return glowCenterY; }

    private static float clampCenter(float value) {
        return Float.isFinite(value) ? clamp01(value) : 0.5f;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
