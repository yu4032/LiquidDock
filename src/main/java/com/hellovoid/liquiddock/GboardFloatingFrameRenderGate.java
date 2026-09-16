package com.hellovoid.liquiddock;

/**
 * Android-free latest-revision render gate matching Launcher's shared output scheduling semantics.
 * At most one render-thread drain is queued; bursts collapse to the newest published revision.
 */
final class GboardFloatingFrameRenderGate {
    private long latestRevision = -1L;
    private long consumedRevision = -1L;
    private boolean queued;
    private boolean cancelled;

    synchronized boolean publish(long revision) {
        if (cancelled || revision < 0L) return false;
        if (revision > latestRevision) latestRevision = revision;
        if (queued) return false;
        queued = true;
        return true;
    }

    synchronized long beginDrain() {
        if (cancelled || !queued) return -1L;
        if (latestRevision <= consumedRevision) {
            queued = false;
            return -1L;
        }
        consumedRevision = latestRevision;
        return consumedRevision;
    }

    synchronized long nextOrIdle() {
        if (cancelled || !queued) return -1L;
        if (latestRevision > consumedRevision) {
            consumedRevision = latestRevision;
            return consumedRevision;
        }
        queued = false;
        return -1L;
    }

    synchronized boolean isQueued() {
        return queued && !cancelled;
    }

    synchronized void cancel() {
        cancelled = true;
        queued = false;
        latestRevision = -1L;
        consumedRevision = -1L;
    }
}
