package com.hellovoid.liquiddock;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe latest-only render gate.
 *
 * <p>Any number of producer signals collapse into at most one scheduled render plus one follow-up
 * when a newer signal arrives while that render is in progress. The gate stores no historical
 * frame count and therefore cannot replay stale producer notifications.
 */
final class LatestFrameRenderGate {
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    /**
     * Marks the latest producer state dirty.
     *
     * @return true only for the caller that acquires ownership of the single pending render.
     */
    boolean request() {
        dirty.set(true);
        return scheduled.compareAndSet(false, true);
    }

    /** Called by the render owner immediately before sampling the newest SurfaceTexture image. */
    void beginRender() {
        dirty.set(false);
    }

    /**
     * Releases render ownership and reacquires it only when a newer producer signal arrived while
     * rendering. A racing request that already acquired ownership wins and prevents duplication.
     */
    boolean finishRenderAndNeedsFollowUp() {
        scheduled.set(false);
        return dirty.get() && scheduled.compareAndSet(false, true);
    }
}
