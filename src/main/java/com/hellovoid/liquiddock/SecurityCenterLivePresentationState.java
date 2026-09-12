package com.hellovoid.liquiddock;

/** Tracks when a strict Security Center presentation has safely graduated to live rendering. */
final class SecurityCenterLivePresentationState {
    private long awaitingGeneration = -1L;
    private long liveGeneration = -1L;

    synchronized void beginStrictPresentation(long generation) {
        if (generation < 0L) {
            invalidate();
            return;
        }
        awaitingGeneration = generation;
        liveGeneration = -1L;
    }

    synchronized void onStrictPresentation(long generation, boolean requestedFollowUp) {
        if (generation < 0L) return;
        if (requestedFollowUp) {
            beginStrictPresentation(generation);
            return;
        }
        if (awaitingGeneration == generation) {
            liveGeneration = generation;
            awaitingGeneration = -1L;
        } else {
            invalidate();
        }
    }

    synchronized boolean isLive(long generation) {
        return generation >= 0L && generation == liveGeneration;
    }

    synchronized void invalidate() {
        awaitingGeneration = -1L;
        liveGeneration = -1L;
    }
}
