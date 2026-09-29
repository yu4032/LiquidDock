package com.hellovoid.liquiddock;

/**
 * Android-free state for the Launcher 4.50 / OS4-style side-slide Sidebar extension.
 *
 * <p>Dwell only arms the gesture. Release commits it. This mirrors OS4's long-click flow:
 * 300 ms -> haptic/confirmation animation -> ACTION_UP -> Sidebar commit.</p>
 */
final class SideSlideHoldPolicy {
    static final long HOLD_DWELL_MS = 300L;

    private boolean active;
    private boolean eligible;
    private boolean armRequested;
    private boolean armed;
    private boolean releaseCommitted;
    private int generation;

    void onDown() {
        active = true;
        eligible = false;
        armRequested = false;
        armed = false;
        releaseCommitted = false;
        generation++;
    }

    boolean onSecondStageProgress(boolean reachedThreshold) {
        return setEligible(reachedThreshold);
    }

    private boolean setEligible(boolean nextEligible) {
        if (!active || releaseCommitted) return false;
        if (nextEligible == eligible) return false;
        eligible = nextEligible;
        armRequested = false;
        armed = false;
        generation++;
        return nextEligible;
    }

    int generation() {
        return generation;
    }

    boolean requestArm(int expectedGeneration) {
        if (!active || !eligible || armRequested || armed || releaseCommitted
                || generation != expectedGeneration) {
            return false;
        }
        armRequested = true;
        return true;
    }

    boolean onArmResult(boolean ready, int expectedGeneration) {
        if (!active || !eligible || !armRequested || releaseCommitted
                || generation != expectedGeneration) {
            return false;
        }
        armRequested = false;
        armed = ready;
        return armed;
    }

    boolean isEligible() {
        return active && eligible && !releaseCommitted;
    }

    boolean isArmed() {
        return active && eligible && armed && !releaseCommitted;
    }

    boolean commitRelease(int expectedGeneration) {
        if (!isArmed() || generation != expectedGeneration) return false;
        releaseCommitted = true;
        return true;
    }

    boolean shouldConsumeVendorCompletion() {
        return active && releaseCommitted;
    }

    void onFinish() {
        active = false;
        eligible = false;
        armRequested = false;
        armed = false;
        releaseCommitted = false;
        generation++;
    }
}
