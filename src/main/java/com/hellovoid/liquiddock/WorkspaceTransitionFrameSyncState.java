package com.hellovoid.liquiddock;

/**
 * Frame-lifecycle state for temporary high-refresh Workspace PassBlur during native motion.
 *
 * <p>Geometry motion covers per-view Launcher animation such as unlock/user-present. Native
 * WindowElement spring ownership covers whole-surface transitions whose transform can be invisible
 * in root-relative child geometry. The two sources are ORed: the ordinary Workspace FPS policy is
 * restored only after both authorities have settled.</p>
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

    private boolean geometryActive;
    private boolean nativeTransitionActive;
    private boolean active;
    private int stableFrames;

    Decision onPreDraw(boolean geometryChanged) {
        if (geometryChanged) {
            stableFrames = 0;
            geometryActive = true;
        } else if (geometryActive) {
            stableFrames++;
            if (stableFrames >= STABLE_FRAMES_TO_RELEASE) {
                stableFrames = 0;
                geometryActive = false;
            }
        }
        return updateAggregate();
    }

    Decision onNativeTransition(boolean enabled) {
        nativeTransitionActive = enabled;
        return updateAggregate();
    }

    Decision reset() {
        geometryActive = false;
        nativeTransitionActive = false;
        stableFrames = 0;
        return updateAggregate();
    }

    boolean needsGeometrySettleFrame() {
        return geometryActive;
    }

    boolean isActive() {
        return active;
    }

    private Decision updateAggregate() {
        boolean next = geometryActive || nativeTransitionActive;
        if (next == active) return Decision.NONE;
        active = next;
        return next ? Decision.enable() : Decision.disable();
    }
}
