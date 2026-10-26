package com.hellovoid.liquiddock;

/**
 * Pure-Java, fail-closed ordering gate for a HandleMenu PassBlur producer.
 *
 * Native detach must succeed before the renderer may release the producer SurfaceTexture.
 * An exception or rejected handler post never advances the gate irreversibly.
 */
final class HandleMenuTeardownGate {
    interface DetachAttempt {
        boolean detach() throws Throwable;
    }

    private boolean detached;
    private boolean releaseQueued;
    private int failedDetachAttempts;

    synchronized boolean attemptDetach(DetachAttempt action) {
        if (detached) return true;
        try {
            if (action != null && action.detach()) {
                detached = true;
                return true;
            }
        } catch (Throwable ignored) {
            // Native errors are logged by the bridge; this gate preserves retryability.
        }
        failedDetachAttempts++;
        return false;
    }

    synchronized boolean claimRenderRelease() {
        if (!detached || releaseQueued) return false;
        releaseQueued = true;
        return true;
    }

    synchronized void renderPostRejected() {
        releaseQueued = false;
    }

    synchronized boolean isDetached() {
        return detached;
    }

    synchronized int failedDetachAttempts() {
        return failedDetachAttempts;
    }
}
