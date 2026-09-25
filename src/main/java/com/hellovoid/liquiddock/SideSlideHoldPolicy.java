package com.hellovoid.liquiddock;

/** Android-free policy for upgrading a completed edge Back drag into Sidebar on hold. */
final class SideSlideHoldPolicy {
    static final float BACK_COMPLETE_DISTANCE_PX = 180f;
    static final float STABILITY_SLOP_PX = 20f;
    static final long HOLD_DWELL_MS = 600L;

    private boolean active;
    private float downX;
    private float anchorX;
    private float anchorY;
    private long eligibleSinceMs = -1L;
    private boolean requestIssued;
    private boolean consumeBack;
    private int generation;

    void onDown(float x, float y, long nowMs) {
        active = true;
        downX = x;
        anchorX = x;
        anchorY = y;
        eligibleSinceMs = -1L;
        requestIssued = false;
        consumeBack = false;
        generation++;
    }

    void onMove(float x, float y, long nowMs) {
        if (!active || consumeBack) return;

        if (Math.abs(x - downX) < BACK_COMPLETE_DISTANCE_PX) {
            if (eligibleSinceMs >= 0L || requestIssued) {
                resetEligibility();
                consumeBack = false;
                generation++;
            }
            return;
        }

        if (eligibleSinceMs < 0L) {
            anchorX = x;
            anchorY = y;
            eligibleSinceMs = nowMs;
            requestIssued = false;
            generation++;
            return;
        }

        if (Math.abs(x - anchorX) > STABILITY_SLOP_PX
                || Math.abs(y - anchorY) > STABILITY_SLOP_PX) {
            anchorX = x;
            anchorY = y;
            eligibleSinceMs = nowMs;
            requestIssued = false;
            consumeBack = false;
            generation++;
        }
    }

    boolean shouldRequestSidebar(long nowMs) {
        if (!active || consumeBack || requestIssued || eligibleSinceMs < 0L) return false;
        if (nowMs - eligibleSinceMs < HOLD_DWELL_MS) return false;
        requestIssued = true;
        return true;
    }

    void onSidebarResult(boolean success, int resultGeneration) {
        if (!active || resultGeneration != generation || !requestIssued) return;
        consumeBack = success;
    }

    boolean shouldConsumeBack() {
        return active && consumeBack;
    }

    int generation() {
        return generation;
    }

    void onUpOrCancel() {
        active = false;
        resetEligibility();
        consumeBack = false;
        generation++;
    }

    private void resetEligibility() {
        eligibleSinceMs = -1L;
        requestIssued = false;
    }
}
