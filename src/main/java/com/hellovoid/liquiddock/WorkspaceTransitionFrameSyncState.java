package com.hellovoid.liquiddock;

/**
 * Frame-lifecycle state for temporary high-refresh Workspace PassBlur during native motion.
 *
 * <p>Geometry motion covers per-view Launcher animation such as unlock/user-present. Native
 * WindowElement spring ownership covers whole-surface transitions whose transform can be invisible
 * in root-relative child geometry. Unlock adds a third authority: once real Workspace motion starts,
 * that lease stays active until keyguard GONE so intermittent stable frames cannot churn
 * force-refresh on/off. The sources are ORed.</p>
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
    private boolean unlockTransitionActive;
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

    Decision onUnlockTransition(boolean enabled) {
        unlockTransitionActive = enabled;
        return updateAggregate();
    }

    boolean isUnlockTransitionActive() {
        return unlockTransitionActive;
    }

    Decision reset() {
        geometryActive = false;
        nativeTransitionActive = false;
        unlockTransitionActive = false;
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
        boolean next = geometryActive || nativeTransitionActive || unlockTransitionActive;
        if (next == active) return Decision.NONE;
        active = next;
        return next ? Decision.enable() : Decision.disable();
    }
}
