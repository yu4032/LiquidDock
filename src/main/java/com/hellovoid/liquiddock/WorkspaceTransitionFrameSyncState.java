package com.hellovoid.liquiddock;

/**
 * Frame-lifecycle state for temporary high-refresh Workspace PassBlur during native motion.
 *
 * <p>The Launcher scene already compares authoritative old/new glass geometry on every root
 * pre-draw. Reuse that signal instead of guessing vendor animation durations. Two consecutive
 * stable pre-draws release the lease so a single dropped/duplicated animation frame cannot
 * prematurely restore the ordinary Workspace render cap.</p>
 */
final class WorkspaceTransitionFrameSyncState {
    static final int STABLE_FRAMES_TO_RELEASE = 2;

    static final class Decision {
        static final Decision NONE = new Decision(false, false);

        final boolean enable;
        final boolean disable;

        private Decision(boolean enable, boolean disable) {
            this.enable = enable;
            this.disable = disable;
        }

        static Decision enable() { return new Decision(true, false); }
        static Decision disable() { return new Decision(false, true); }
    }

    private boolean active;
    private int stableFrames;

    Decision onPreDraw(boolean geometryChanged) {
        if (geometryChanged) {
            stableFrames = 0;
            if (!active) {
                active = true;
                return Decision.enable();
            }
            return Decision.NONE;
        }
        if (!active) return Decision.NONE;

        stableFrames++;
        if (stableFrames < STABLE_FRAMES_TO_RELEASE) return Decision.NONE;

        active = false;
        stableFrames = 0;
        return Decision.disable();
    }

    Decision reset() {
        stableFrames = 0;
        if (!active) return Decision.NONE;
        active = false;
        return Decision.disable();
    }

    boolean isActive() {
        return active;
    }
}
