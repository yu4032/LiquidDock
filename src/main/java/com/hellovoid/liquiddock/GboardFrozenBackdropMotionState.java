package com.hellovoid.liquiddock;

/**
 * Tracks frozen-backdrop drag boundaries without timers.
 *
 * <p>The first translated frame after a stable period requests one fresh source frame. Continuous
 * translated frames remain inside the same motion and do not request more source updates. Two
 * stable frames arm the next movement start.</p>
 */
final class GboardFrozenBackdropMotionState {
    private static final int STABLE_FRAMES_TO_REARM = 2;

    private boolean moving;
    private int stableFrames;

    boolean onFrame(boolean translated) {
        if (translated) {
            stableFrames = 0;
            if (!moving) {
                moving = true;
                return true;
            }
            return false;
        }

        if (!moving) return false;
        stableFrames++;
        if (stableFrames >= STABLE_FRAMES_TO_REARM) {
            moving = false;
            stableFrames = 0;
        }
        return false;
    }

    void reset() {
        moving = false;
        stableFrames = 0;
    }
}
