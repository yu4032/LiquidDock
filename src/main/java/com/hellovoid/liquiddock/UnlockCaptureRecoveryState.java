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
        // Preserve the active serial only while a real rollover completion is still in flight.
        // A failed/stale blocked cycle has no completion authority left, so the next PREPARE must
        // be able to arm a new recovery cycle instead of turning blocked into a permanent latch.
        if (blocked && rolloverRequested) {
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
            // The current cycle remains fail-closed, but this completion is terminal: it is no
            // longer an in-flight rollover that may protect the serial from the next PREPARE.
            rolloverRequested = false;
            return new Decision(false, false, false, activeSerial);
        }
        blocked = false;
        rolloverRequested = false;
        return new Decision(false, false, true, activeSerial);
    }

    synchronized Decision onBarrierTimeout(long serial) {
        if (!blocked || serial != activeSerial) {
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
