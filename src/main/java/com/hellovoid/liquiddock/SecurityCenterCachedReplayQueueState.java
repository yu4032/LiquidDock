package com.hellovoid.liquiddock;

/**
 * Android-free coalescing state for cached Security Center geometry replay.
 *
 * One render-thread task owns one queue turn. Requests that arrive while that task is queued or
 * running only mark the state dirty. Completion may authorize exactly one tail repost; it never
 * authorizes an inline drain loop, so output detach/resize/release work cannot be starved by a
 * continuous morph animation.
 */
final class SecurityCenterCachedReplayQueueState {
    private boolean queued;
    private boolean dirty;

    synchronized boolean request() {
        if (queued) {
            dirty = true;
            return false;
        }
        queued = true;
        dirty = false;
        return true;
    }

    /**
     * Completes one queue turn. Returns true when one tail repost is required and reserves that
     * repost immediately so concurrent requests coalesce into it.
     */
    synchronized boolean complete() {
        if (!queued) return false;
        if (dirty) {
            dirty = false;
            // Keep ownership reserved for the one tail repost.
            return true;
        }
        queued = false;
        return false;
    }

    synchronized void cancelQueued() {
        queued = false;
        dirty = false;
    }

    synchronized void reset() {
        queued = false;
        dirty = false;
    }
}