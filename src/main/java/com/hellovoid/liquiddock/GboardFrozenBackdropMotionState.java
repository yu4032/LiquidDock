package com.hellovoid.liquiddock;

/**
 * Tracks frozen-backdrop lifecycle without timers.
 *
 * <p>Startup remains live until the popup has been presented, the coordinator's minimum live
 * window has elapsed, and geometry has stayed stable for several frames. After startup freezes,
 * the first movement frame requests exactly one fresh backdrop and continuous movement stays
 * frozen. Two stable frames arm the next movement start.</p>
 */
final class GboardFrozenBackdropMotionState {
    enum Decision {
        NONE,
        FREEZE_AFTER_SETTLE,
        REFRESH_AT_MOTION_START
    }

    private static final int STARTUP_STABLE_FRAMES_TO_FREEZE = 6;
    private static final int STABLE_FRAMES_TO_REARM = 2;

    private boolean startup = true;
    private boolean moving;
    private int stableFrames;

    Decision onFrame(boolean startupFreezeAllowed, boolean geometryChanged) {
        if (startup) {
            if (!startupFreezeAllowed) {
                stableFrames = 0;
                return Decision.NONE;
            }
            if (geometryChanged) {
                stableFrames = 0;
                return Decision.NONE;
            }
            stableFrames++;
            if (stableFrames >= STARTUP_STABLE_FRAMES_TO_FREEZE) {
                startup = false;
                moving = false;
                stableFrames = 0;
                return Decision.FREEZE_AFTER_SETTLE;
            }
            return Decision.NONE;
        }

        if (geometryChanged) {
            stableFrames = 0;
            if (!moving) {
                moving = true;
                return Decision.REFRESH_AT_MOTION_START;
            }
            return Decision.NONE;
        }

        if (!moving) return Decision.NONE;
        stableFrames++;
        if (stableFrames >= STABLE_FRAMES_TO_REARM) {
            moving = false;
            stableFrames = 0;
        }
        return Decision.NONE;
    }

    boolean isStartup() {
        return startup;
    }

    void reset() {
        startup = true;
        moving = false;
        stableFrames = 0;
    }
}
