package com.hellovoid.liquiddock;

/**
 * Scheduling state for Dock backdrop motion.
 *
 * Every vendor motion callback must refresh sampling immediately. Only the continuation VSYNC is
 * coalesced; coalescing must never suppress the current frame's mapping update.
 */
final class DockBackdropMotionSyncState {
    static final class Decision {
        final boolean refreshMappingNow;
        final boolean scheduleContinuation;

        Decision(boolean refreshMappingNow, boolean scheduleContinuation) {
            this.refreshMappingNow = refreshMappingNow;
            this.scheduleContinuation = scheduleContinuation;
        }
    }

    private boolean continuationPending;

    synchronized Decision onVendorMotionFrame() {
        boolean schedule = !continuationPending;
        if (schedule) continuationPending = true;
        return new Decision(true, schedule);
    }

    synchronized void onContinuationVsync() {
        continuationPending = false;
    }

    synchronized void reset() {
        continuationPending = false;
    }
}
