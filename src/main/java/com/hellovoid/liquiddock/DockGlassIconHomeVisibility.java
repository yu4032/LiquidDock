package com.hellovoid.liquiddock;

/**
 * HOME spring-owned visibility of the Dock icon scene, independent of the Dock body.
 * Progress is the same native FloatingIcon progress used for per-icon glass reveal.
 */
final class DockGlassIconHomeVisibility {
    private boolean returning;
    private float alpha = 1f;

    boolean begin() {
        if (returning) return false;
        returning = true;
        alpha = 0f;
        return true;
    }

    boolean observe(float progress) {
        if (!returning || !Float.isFinite(progress)) return false;
        float next = LauncherGlassReturnFade.alpha(progress);
        if (Float.compare(alpha, next) == 0) return false;
        alpha = next;
        return true;
    }

    boolean finish() {
        boolean changed = returning || alpha != 1f;
        returning = false;
        alpha = 1f;
        return changed;
    }

    float alpha() {
        return alpha;
    }
}
