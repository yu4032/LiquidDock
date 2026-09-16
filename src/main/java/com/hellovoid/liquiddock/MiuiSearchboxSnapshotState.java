package com.hellovoid.liquiddock;

/** One-shot freshness gate: one authoritative backdrop is consumed per visible Searchbox cycle. */
final class MiuiSearchboxSnapshotState {
    private boolean capturePending;
    private boolean snapshotLatched;

    synchronized void beginCapture() {
        capturePending = true;
        snapshotLatched = false;
    }

    synchronized boolean acceptFreshFrame() {
        if (!capturePending) return false;
        capturePending = false;
        snapshotLatched = true;
        return true;
    }

    synchronized boolean isLatched() {
        return snapshotLatched;
    }
}
