package com.hellovoid.liquiddock;

/** Exactly-once terminal gate for one Dock frame-commit handoff attempt. */
final class DockFrameCommitHandoffState {
    private boolean terminal;

    synchronized boolean completeIfPending() {
        if (terminal) return false;
        terminal = true;
        return true;
    }

    synchronized void cancel() {
        terminal = true;
    }
}
