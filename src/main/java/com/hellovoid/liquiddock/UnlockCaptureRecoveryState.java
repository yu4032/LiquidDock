package com.hellovoid.liquiddock;

/** Android-free state machine for fail-closed unlock producer recovery. */
final class UnlockCaptureRecoveryState {
    enum Phase {
        IDLE,
        BLOCKED_WAITING_GONE,
        ROLLING_OVER,
        RECOVERED,
        FAILED_CLOSED
    }

    static final class Decision {
        final boolean suspendProducers;
        final boolean requestRollover;
        final boolean releaseBarrier;
        final boolean failedClosed;
        final long serial;

        Decision(
                boolean suspendProducers,
                boolean requestRollover,
                boolean releaseBarrier,
                boolean failedClosed,
                long serial) {
            this.suspendProducers = suspendProducers;
            this.requestRollover = requestRollover;
            this.releaseBarrier = releaseBarrier;
            this.failedClosed = failedClosed;
            this.serial = serial;
        }
    }

    private long nextSerial;
    private long activeSerial;
    private Phase phase = Phase.IDLE;

    synchronized Decision onPrepare() {
        // PREPARE is a new lifecycle authority even if an older rollover is still completing.
        // Advancing the serial makes every completion from the previous unlock cycle stale.
        activeSerial = ++nextSerial;
        phase = Phase.BLOCKED_WAITING_GONE;
        return decision(true, false, false, false);
    }

    synchronized Decision onSystemUiGoneFinished() {
        boolean suspend = false;
        if (phase == Phase.IDLE || phase == Phase.RECOVERED) {
            // Failsafe for devices where Launcher PREPARE was missed: arm capture protection before
            // requesting a replacement endpoint.
            activeSerial = ++nextSerial;
            phase = Phase.BLOCKED_WAITING_GONE;
            suspend = true;
        } else if (phase == Phase.FAILED_CLOSED) {
            // A failed cycle cannot regain authority from a repeated FINISHED callback. Only a new
            // PREPARE starts another recovery cycle.
            return decision(false, false, false, true);
        }

        if (phase == Phase.ROLLING_OVER) {
            return decision(false, false, false, false);
        }
        if (phase != Phase.BLOCKED_WAITING_GONE) {
            return decision(false, false, false, false);
        }

        phase = Phase.ROLLING_OVER;
        return decision(suspend, true, false, false);
    }

    synchronized Decision onRolloverFinished(long serial, boolean success) {
        if (serial != activeSerial || phase != Phase.ROLLING_OVER) {
            return decision(false, false, false, phase == Phase.FAILED_CLOSED);
        }
        if (!success) {
            phase = Phase.FAILED_CLOSED;
            return decision(false, false, false, true);
        }

        phase = Phase.RECOVERED;
        return decision(false, false, true, false);
    }

    /**
     * Optional watchdog transition. Timeout is diagnostic/fallback authority only: it can mark the
     * active cycle terminally failed, but it can never release LiquidDock capture authority.
     */
    synchronized Decision onWatchdogTimeout(long serial) {
        if (serial != activeSerial || !isBlocked()) {
            return decision(false, false, false, phase == Phase.FAILED_CLOSED);
        }
        phase = Phase.FAILED_CLOSED;
        return decision(false, false, false, true);
    }

    synchronized boolean isBlocked() {
        return phase == Phase.BLOCKED_WAITING_GONE
                || phase == Phase.ROLLING_OVER
                || phase == Phase.FAILED_CLOSED;
    }

    synchronized Phase phase() {
        return phase;
    }

    synchronized long activeSerial() {
        return activeSerial;
    }

    private Decision decision(
            boolean suspendProducers,
            boolean requestRollover,
            boolean releaseBarrier,
            boolean failedClosed) {
        return new Decision(
                suspendProducers,
                requestRollover,
                releaseBarrier,
                failedClosed,
                activeSerial);
    }
}
