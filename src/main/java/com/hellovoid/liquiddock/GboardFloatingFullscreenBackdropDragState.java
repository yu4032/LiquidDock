package com.hellovoid.liquiddock;

/**
 * Android-free state for reusing one prepared full-root backdrop while floating Gboard moves.
 * Geometry rendering remains live during drag; only source/backdrop refresh is frozen.
 */
final class GboardFloatingFullscreenBackdropDragState {
    enum Mode { LIVE, DRAG, RECOVERING }

    static final class Recovery {
        final boolean resumeSourceUpdates;
        final boolean reconcileRoot;
        final boolean requestFresh;
        final long generation;

        private Recovery(
                boolean resumeSourceUpdates,
                boolean reconcileRoot,
                boolean requestFresh,
                long generation) {
            this.resumeSourceUpdates = resumeSourceUpdates;
            this.reconcileRoot = reconcileRoot;
            this.requestFresh = requestFresh;
            this.generation = generation;
        }

        static Recovery none(long generation) {
            return new Recovery(false, false, false, generation);
        }

        static Recovery refresh(long generation) {
            return new Recovery(true, true, true, generation);
        }
    }

    private Mode mode = Mode.LIVE;
    private long requestedGeneration;
    private long backdropGeneration = -1L;
    private boolean backdropPresented;
    private boolean recoveryBackdropPrepared;

    GboardFloatingFullscreenBackdropDragState(long initialGeneration) {
        requestedGeneration = Math.max(0L, initialGeneration);
    }

    synchronized Mode mode() {
        return mode;
    }

    synchronized long requestedGeneration() {
        return requestedGeneration;
    }

    synchronized long backdropGeneration() {
        return backdropGeneration;
    }

    synchronized boolean beginDrag() {
        if (mode != Mode.LIVE || !backdropPresented || backdropGeneration < 0L) return false;
        mode = Mode.DRAG;
        recoveryBackdropPrepared = false;
        return true;
    }

    synchronized Recovery endDrag() {
        if (mode != Mode.DRAG) return Recovery.none(requestedGeneration);
        requestedGeneration++;
        mode = Mode.RECOVERING;
        recoveryBackdropPrepared = false;
        return Recovery.refresh(requestedGeneration);
    }

    synchronized boolean shouldPauseSourceUpdates() {
        return mode == Mode.DRAG;
    }

    synchronized boolean shouldRenderGeometry() {
        return backdropGeneration >= 0L;
    }

    synchronized boolean shouldPrepareBackdrop(long generation) {
        if (mode == Mode.DRAG) return false;
        if (generation != requestedGeneration) return false;
        return mode == Mode.LIVE || mode == Mode.RECOVERING;
    }

    synchronized void onBackdropPrepared(long generation) {
        if (!shouldPrepareBackdrop(generation)) return;
        backdropGeneration = generation;
        backdropPresented = false;
        recoveryBackdropPrepared = mode == Mode.RECOVERING;
    }

    synchronized void onOutputPresented() {
        if (backdropGeneration < 0L) return;
        backdropPresented = true;
        if (mode == Mode.RECOVERING && recoveryBackdropPrepared
                && backdropGeneration == requestedGeneration) {
            mode = Mode.LIVE;
            recoveryBackdropPrepared = false;
        }
    }
}
