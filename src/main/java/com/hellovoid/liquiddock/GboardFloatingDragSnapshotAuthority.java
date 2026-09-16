package com.hellovoid.liquiddock;

/** Typed drag-time authority for one immutable full-root backdrop snapshot. */
final class GboardFloatingDragSnapshotAuthority {
    static final class Decision {
        static final Decision NONE = new Decision(false, false, false, false, false);

        final boolean latchSnapshot;
        final boolean pauseLiveSource;
        final boolean resumeLiveSource;
        final boolean reconcileProducer;
        final boolean requestFreshBackdrop;

        Decision(
                boolean latchSnapshot,
                boolean pauseLiveSource,
                boolean resumeLiveSource,
                boolean reconcileProducer,
                boolean requestFreshBackdrop) {
            this.latchSnapshot = latchSnapshot;
            this.pauseLiveSource = pauseLiveSource;
            this.resumeLiveSource = resumeLiveSource;
            this.reconcileProducer = reconcileProducer;
            this.requestFreshBackdrop = requestFreshBackdrop;
        }
    }

    private boolean dragging;

    synchronized Decision onDragStarted(boolean hasPreparedBackdrop) {
        if (dragging || !hasPreparedBackdrop) return Decision.NONE;
        dragging = true;
        return new Decision(true, true, false, false, false);
    }

    synchronized Decision onDragEnded() {
        if (!dragging) return Decision.NONE;
        dragging = false;
        return new Decision(false, false, true, true, true);
    }

    synchronized boolean isDragging() {
        return dragging;
    }

    synchronized boolean acceptLiveBackdrop() {
        return !dragging;
    }

    synchronized boolean allowGeometryRender() {
        return true;
    }

    synchronized boolean allowPrepareBackdrop() {
        return !dragging;
    }

    synchronized boolean allowProducerReconcile() {
        return !dragging;
    }

    synchronized boolean allowFreshRequest() {
        return !dragging;
    }
}
