package com.hellovoid.liquiddock;

/** Shared timing policy for reversible Launcher glass visibility transitions. */
final class LauncherGlassVisibilityTransition {
    static final class Plan {
        final float startAlpha;
        final float targetAlpha;
        final long durationMs;

        Plan(float startAlpha, float targetAlpha, long durationMs) {
            this.startAlpha = startAlpha;
            this.targetAlpha = targetAlpha;
            this.durationMs = durationMs;
        }
    }

    static Plan plan(float currentAlpha, boolean visible) {
        return plan(currentAlpha, visible,
                AnimationRuntimeState.workspaceVisibilityDurationMs());
    }

    static Plan plan(float currentAlpha, boolean visible, long fullDurationMs) {
        float start = Float.isFinite(currentAlpha)
                ? Math.max(0f, Math.min(1f, currentAlpha)) : 0f;
        float target = visible ? 1f : 0f;
        long duration = Math.round(Math.max(0L, fullDurationMs)
                * Math.abs(target - start));
        return new Plan(start, target, duration);
    }

    private LauncherGlassVisibilityTransition() {}
}
