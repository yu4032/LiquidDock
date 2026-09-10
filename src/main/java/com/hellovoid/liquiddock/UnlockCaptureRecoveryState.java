package com.hellovoid.liquiddock;

/** Android-free state machine for fail-closed unlock producer recovery. */
final class UnlockCaptureRecoveryState {
    static final class Decision {
        final boolean suspendProducers;
        final boolean requestRollover;
        final boolean releaseBarrier;
        final long serial;

        Decision(
                boolean suspendProducers,
                boolean requestRollover,
                boolean releaseBarrier,
                long serial) {
            this.suspendProducers = suspendProducers;
            this.requestRollover = requestRollover;
            this.releaseBarrier = releaseBarrier;
            this.serial = serial;
        }
    }

    private long nextSerial;
    private long activeSerial;
    private boolean blocked;
    private boolean rolloverRequested;

    synchronized Decision onPrepare() {
        // PREPARE may be emitted more than once for one keyguard transition. Once a recovery
        // cycle is blocked, keep its serial and any in-flight rollover authority intact; otherwise
        // the old completion can never release the barrier after a duplicate PREPARE.
        if (blocked) {
            return new Decision(false, false, false, activeSerial);
        }
        activeSerial = ++nextSerial;
        blocked = true;
        rolloverRequested = false;
        return new Decision(true, false, false, activeSerial);
    }

    synchronized Decision onSystemUiGoneFinished() {
        boolean suspend = false;
        if (!blocked) {
            activeSerial = ++nextSerial;
            blocked = true;
            rolloverRequested = false;
            suspend = true;
        }
        if (rolloverRequested) {
            return new Decision(false, false, false, activeSerial);
        }
        rolloverRequested = true;
        return new Decision(suspend, true, false, activeSerial);
    }

    synchronized Decision onRolloverFinished(long serial, boolean success) {
        if (!blocked || !rolloverRequested || serial != activeSerial) {
            return new Decision(false, false, false, activeSerial);
        }
        if (!success) {
            return new Decision(false, false, false, activeSerial);
        }
        blocked = false;
        rolloverRequested = false;
        return new Decision(false, false, true, activeSerial);
    }

    synchronized boolean isBlocked() {
        return blocked;
    }
}
