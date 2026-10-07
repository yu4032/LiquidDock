package com.hellovoid.liquiddock;

/** Coalesces geometry work with source-driven output rendering without allocating per frame. */
final class LauncherGlassOutputRenderState {
    static final int STATIC = 1;
    static final int DRAG = 2;

    private int pending;
    private boolean queued;

    synchronized boolean request(boolean staticDirty, boolean dragDirty) {
        if (staticDirty) pending |= STATIC;
        if (dragDirty) pending |= DRAG;
        if (queued || pending == 0) return false;
        queued = true;
        return true;
    }

    synchronized int consumeQueuedRender() {
        int work = pending;
        pending = 0;
        return work;
    }

    synchronized void consumeForSourceRender() {
        // The source frame is about to draw both outputs using their latest geometry. Leave
        // queued ownership intact so an existing drain can observe any later UI changes.
        pending = 0;
    }

    synchronized boolean finishQueuedRender() {
        if (pending != 0) return true;
        queued = false;
        return false;
    }

    synchronized void onPostRejected() {
        queued = false;
    }
}
