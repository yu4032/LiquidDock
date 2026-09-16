package com.hellovoid.liquiddock;

/**
 * Coalesces bursty drag-time work into at most one queued task plus one rearm while work runs.
 * Callers own the actual scheduling primitive (VSYNC or render thread).
 */
final class GboardFloatingCoalescingGate {
    private boolean pending;
    private boolean dirty;

    synchronized boolean request() {
        dirty = true;
        if (pending) return false;
        pending = true;
        return true;
    }

    synchronized boolean begin() {
        if (!pending || !dirty) return false;
        dirty = false;
        return true;
    }

    synchronized boolean complete() {
        if (dirty) return true;
        pending = false;
        return false;
    }

    synchronized void cancel() {
        dirty = false;
        pending = false;
    }
}
