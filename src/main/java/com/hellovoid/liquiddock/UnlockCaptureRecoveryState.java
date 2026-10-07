package com.hellovoid.liquiddock;

/**
 * Android-free unlock capture barrier.
 *
 * <p>PREPARE gates freshness and initially pauses source updates. Once native Workspace motion
 * starts, production may resume the existing producer while this barrier remains active; those
 * frames are live presentation only and are not allowed to satisfy the next scene generation.
 * SystemUI GONE releases the barrier immediately. Root endpoint reconciliation then performs a
 * rebind only if the authoritative endpoint generation actually changed.</p>
 */
final class UnlockCaptureRecoveryState {
    static final class Decision {
        final boolean suspendProducers;
        final boolean releaseBarrier;
        final long serial;

        Decision(boolean suspendProducers, boolean releaseBarrier, long serial) {
            this.suspendProducers = suspendProducers;
            this.releaseBarrier = releaseBarrier;
            this.serial = serial;
        }
    }

    private long nextSerial;
    private long activeSerial;
    private boolean blocked;

    synchronized Decision onPrepare() {
        if (blocked) return new Decision(false, false, activeSerial);
        activeSerial = ++nextSerial;
        blocked = true;
        return new Decision(true, false, activeSerial);
    }

    synchronized Decision onSystemUiGoneFinished() {
        if (!blocked) return new Decision(false, false, activeSerial);
        blocked = false;
        return new Decision(false, true, activeSerial);
    }

    synchronized Decision onBarrierTimeout(long serial) {
        if (!blocked || serial != activeSerial) {
            return new Decision(false, false, activeSerial);
        }
        blocked = false;
        return new Decision(false, true, activeSerial);
    }

    synchronized boolean isBlocked() {
        return blocked;
    }
}
