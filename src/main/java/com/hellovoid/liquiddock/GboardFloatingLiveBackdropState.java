package com.hellovoid.liquiddock;

/** Authority state for drag snapshot handoff back to a fresh live backdrop. */
final class GboardFloatingLiveBackdropState {
    enum Phase { LIVE, DRAG_SNAPSHOT, WAITING_FOR_FRESH_LIVE }

    private Phase phase = Phase.LIVE;
    private long waitingGeneration = -1L;
    private boolean freshPrepared;

    synchronized boolean onDragStart(boolean prepared) {
        if (phase != Phase.LIVE || !prepared) return false;
        phase = Phase.DRAG_SNAPSHOT;
        return true;
    }

    synchronized void onDragEnd(long generation) {
        if (phase != Phase.DRAG_SNAPSHOT) return;
        phase = Phase.WAITING_FOR_FRESH_LIVE;
        waitingGeneration = generation;
        freshPrepared = false;
    }

    synchronized boolean acceptLiveBackdrop() {
        return phase != Phase.DRAG_SNAPSHOT;
    }

    synchronized void onFreshBackdropPrepared(long generation) {
        if (phase == Phase.WAITING_FOR_FRESH_LIVE && generation == waitingGeneration) {
            freshPrepared = true;
        }
    }

    synchronized void onFreshOutputSwapped(long generation) {
        if (phase == Phase.WAITING_FOR_FRESH_LIVE
                && freshPrepared && generation == waitingGeneration) {
            phase = Phase.LIVE;
            waitingGeneration = -1L;
            freshPrepared = false;
        }
    }

    synchronized Phase phase() {
        return phase;
    }
}
