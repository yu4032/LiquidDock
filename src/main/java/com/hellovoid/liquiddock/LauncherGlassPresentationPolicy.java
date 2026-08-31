package com.hellovoid.liquiddock;

/** Pure policy for combining native Workspace presentation with LiquidDock suppression. */
final class LauncherGlassPresentationPolicy {
    private static final float EPSILON = 0.001f;

    enum Mode { BYPASS, HOLD, ANIMATE }

    static final class Decision {
        final Mode mode;
        final float targetAlpha;
        final boolean retainLastGeometry;

        Decision(Mode mode, float targetAlpha, boolean retainLastGeometry) {
            this.mode = mode;
            this.targetAlpha = targetAlpha;
            this.retainLastGeometry = retainLastGeometry;
        }
    }

    private LauncherGlassPresentationPolicy() {}

    static Decision decide(
            boolean semanticSceneOwnsVisibility,
            boolean wasAtVisibleEndpoint,
            boolean structurallyVisible,
            float observedAlpha) {
        if (semanticSceneOwnsVisibility) {
            return new Decision(Mode.BYPASS, 1f, true);
        }
        float alpha = clamp01(observedAlpha);
        if (!structurallyVisible || alpha <= EPSILON) {
            return new Decision(Mode.ANIMATE, 0f, true);
        }
        if (!wasAtVisibleEndpoint && alpha >= 1f - EPSILON) {
            return new Decision(Mode.ANIMATE, 1f, false);
        }
        // Continuous vendor/ancestor alpha is sampled every Launcher pre-draw. Mirroring those
        // intermediate values into a Prismal node would force the shared static layer to redraw
        // and eglSwap once per animation frame. Keep the already-rendered node presentation
        // stable until a real visibility endpoint is reached; semantic scene fades are owned by
        // LauncherGlassStaticLayer instead.
        return new Decision(Mode.HOLD, alpha, false);
    }

    static float composeAlpha(float suppressionAlpha, float nativePresentationAlpha) {
        return clamp01(suppressionAlpha) * clamp01(nativePresentationAlpha);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
