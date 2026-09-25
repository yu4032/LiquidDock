package com.hellovoid.liquiddock;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Latest-state render scheduler shared by producer-frame and scene-only requests.
 *
 * <p>At most one runnable is owned at a time. Requests arriving while that runnable is queued or
 * executing collapse into one dirty follow-up. Producer intent is sticky until the next run begins,
 * so an OES frame cannot be downgraded by a later scene-only request.
 */
final class LatestFrameRenderGate {
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean producerDirty = new AtomicBoolean(false);
    private final AtomicLong coalescedRequests = new AtomicLong();

    boolean request(boolean fromProducer) {
        dirty.set(true);
        if (fromProducer) producerDirty.set(true);
        if (scheduled.compareAndSet(false, true)) return true;
        coalescedRequests.incrementAndGet();
        return false;
    }

    boolean beginRun() {
        boolean producerTriggered = producerDirty.getAndSet(false);
        dirty.set(false);
        return producerTriggered;
    }

    boolean finishRunAndClaimFollowUp() {
        scheduled.set(false);
        return dirty.get() && scheduled.compareAndSet(false, true);
    }

    long takeCoalescedRequestCount() {
        return coalescedRequests.getAndSet(0L);
    }
}
