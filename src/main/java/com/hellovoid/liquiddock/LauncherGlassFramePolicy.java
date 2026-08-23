package com.hellovoid.liquiddock;

/** Coalesces Launcher glass invalidations while keeping static and drag work independent. */
final class LauncherGlassFramePolicy {
    private boolean scheduled;
    private boolean refreshProducer;
    private boolean rebuildBackdrop;
    private boolean staticDirty;
    private boolean dragDirty;

    /** Backward-compatible full-scene invalidation. */
    synchronized boolean request(boolean requireProducerRefresh) {
        staticDirty = true;
        dragDirty = true;
        refreshProducer |= requireProducerRefresh;
        rebuildBackdrop |= requireProducerRefresh;
        return schedule();
    }

    synchronized boolean requestStatic() {
        staticDirty = true;
        return schedule();
    }

    synchronized boolean requestDrag() {
        dragDirty = true;
        return schedule();
    }

    synchronized boolean requestBackdropRefresh() {
        staticDirty = true;
        dragDirty = true;
        refreshProducer = true;
        rebuildBackdrop = true;
        return schedule();
    }

    synchronized boolean requestBackdropRebuild() {
        staticDirty = true;
        dragDirty = true;
        rebuildBackdrop = true;
        return schedule();
    }

    private boolean schedule() {
        if (scheduled) return false;
        scheduled = true;
        return true;
    }

    synchronized Work consume() {
        if (!scheduled) return new Work(false, false, false, false, false);
        Work work = new Work(true, refreshProducer, rebuildBackdrop, staticDirty, dragDirty);
        scheduled = false;
        refreshProducer = false;
        rebuildBackdrop = false;
        staticDirty = false;
        dragDirty = false;
        return work;
    }

    static final class Work {
        final boolean render;
        final boolean refreshProducer;
        final boolean rebuildBackdrop;
        final boolean staticDirty;
        final boolean dragDirty;

        Work(boolean render, boolean refreshProducer, boolean rebuildBackdrop,
             boolean staticDirty, boolean dragDirty) {
            this.render = render;
            this.refreshProducer = refreshProducer;
            this.rebuildBackdrop = rebuildBackdrop;
            this.staticDirty = staticDirty;
            this.dragDirty = dragDirty;
        }
    }
}
