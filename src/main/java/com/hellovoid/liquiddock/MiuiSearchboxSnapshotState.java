package com.hellovoid.liquiddock;

/** One-shot freshness gate: one authoritative backdrop is consumed per visible Searchbox cycle. */
final class MiuiSearchboxSnapshotState {
    private boolean capturePending;

    synchronized void beginCapture() {
        capturePending = true;
    }

    synchronized boolean acceptFreshFrame() {
        if (!capturePending) return false;
        capturePending = false;
        return true;
    }
}
