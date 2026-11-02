package com.hellovoid.liquiddock;

/**
 * Defers one Workspace icon's expensive glass until the native FloatingIcon-to-ShortcutIcon
 * handoff completes. The native icon/drawable remains Launcher-owned throughout the transition.
 */
final class LauncherGlassIconReturnReveal {
    static final long MAX_FADE_MS = 220L;

    private boolean sourceRestored;
    private boolean sourceHidden;
    private boolean returning;
    private float glassAlpha = 1f;

    /** A real native source hide starts a new cycle and retires the previous terminal marker. */
    void onNativeHidden() {
        // Launcher may repeat the same concealment callback, or publish the first proxy
        // frame slightly ahead of it. Neither event may erase an active HOME return.
        if (sourceHidden && !sourceRestored) return;
        sourceHidden = true;
        sourceRestored = false;
        glassAlpha = 0f;
    }

    /** Late FloatingIcon updates from the completed cycle cannot re-hide the source glass. */
    boolean acceptsProxyUpdates() {
        return !sourceRestored;
    }

    void onProxyFrame(boolean closeToHome) {
        if (sourceRestored) return;
        returning = closeToHome;
        // Launch-away retains its moving proxy. The home target waits for native source restore.
        glassAlpha = closeToHome ? 0f : 1f;
    }

    /** @return whether the restored source needs a post-handoff glass-only fade. */
    boolean onNativeVisible() {
        if (sourceRestored) return false;
        sourceRestored = true;
        sourceHidden = false;
        boolean reveal = returning;
        returning = false;
        glassAlpha = reveal ? 0f : 1f;
        return reveal;
    }

    boolean setRevealProgress(float progress) {
        if (!Float.isFinite(progress)) return false;
        float next = Math.max(0f, Math.min(1f, progress));
        if (Float.compare(glassAlpha, next) == 0) return false;
        glassAlpha = next;
        return true;
    }

    void completeReveal() {
        glassAlpha = 1f;
    }

    void reset() {
        sourceRestored = false;
        sourceHidden = false;
        returning = false;
        glassAlpha = 1f;
    }

    float alpha() {
        return glassAlpha;
    }
}
