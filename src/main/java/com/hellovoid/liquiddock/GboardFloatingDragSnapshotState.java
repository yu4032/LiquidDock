package com.hellovoid.liquiddock;

/** Android-free lifecycle for freezing Gboard glass during a real floating-keyboard drag. */
final class GboardFloatingDragSnapshotState {
    static final class Decision {
        static final Decision NONE = new Decision(false, false, false, false);
        final boolean pauseUpdates;
        final boolean resumeUpdates;
        final boolean reconcileRoot;
        final boolean requestFresh;

        Decision(
                boolean pauseUpdates,
                boolean resumeUpdates,
                boolean reconcileRoot,
                boolean requestFresh) {
            this.pauseUpdates = pauseUpdates;
            this.resumeUpdates = resumeUpdates;
            this.reconcileRoot = reconcileRoot;
            this.requestFresh = requestFresh;
        }
    }

    private enum Phase { LIVE, SNAPSHOT_DRAG, RECOVERING }

    private Phase phase = Phase.LIVE;
    private boolean recoveryFreshPrepared;

    synchronized Decision onDragStarted(boolean hasPresentedSnapshot) {
        if (!hasPresentedSnapshot || phase == Phase.SNAPSHOT_DRAG) return Decision.NONE;
        phase = Phase.SNAPSHOT_DRAG;
        recoveryFreshPrepared = false;
        return new Decision(true, false, false, false);
    }

    synchronized Decision onDragEnded() {
        if (phase != Phase.SNAPSHOT_DRAG) return Decision.NONE;
        phase = Phase.RECOVERING;
        recoveryFreshPrepared = false;
        return new Decision(false, true, true, true);
    }

    synchronized void onRecoveryFreshFramePrepared() {
        if (phase == Phase.RECOVERING) recoveryFreshPrepared = true;
    }

    synchronized void onOutputPresented() {
        if (phase == Phase.RECOVERING && recoveryFreshPrepared) {
            phase = Phase.LIVE;
            recoveryFreshPrepared = false;
        }
    }

    synchronized boolean isSnapshotDragging() {
        return phase == Phase.SNAPSHOT_DRAG;
    }

    synchronized boolean isRecovering() {
        return phase == Phase.RECOVERING;
    }

    synchronized boolean shouldAcceptFreshFrame() {
        return phase != Phase.SNAPSHOT_DRAG;
    }

    synchronized boolean shouldSampleGeometry() {
        return phase != Phase.SNAPSHOT_DRAG;
    }
}
