package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

/** Pure-JVM tests for stale preference listener and posted notification generations. */
public class Api102ListenerEpochTest {
    @Test
    public void shutdownAndReinitializationInvalidateOldGeneration() {
        Api102ListenerEpoch epochs = new Api102ListenerEpoch();
        long first = epochs.activate();
        assertTrue(epochs.isCurrent(first));
        epochs.invalidate();
        assertFalse(epochs.isCurrent(first));

        long second = epochs.activate();
        assertTrue(epochs.isCurrent(second));
        assertFalse(epochs.isCurrent(first));
        epochs.invalidate();
        assertFalse(epochs.isCurrent(second));

        long third = epochs.activate();
        assertTrue(epochs.isCurrent(third));
        assertFalse(epochs.isCurrent(second));
    }

    @Test
    public void concurrentStaleListenerNeverReactivatesAfterInvalidation() throws Exception {
        Api102ListenerEpoch epochs = new Api102ListenerEpoch();
        long stale = epochs.activate();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean delivered = new AtomicBoolean(false);
        Thread listener = new Thread(() -> {
            try {
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("latch");
                delivered.set(epochs.isCurrent(stale));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            } finally {
                finished.countDown();
            }
        });
        listener.start();
        epochs.invalidate();
        long next = epochs.activate();
        start.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        listener.join(5000);
        assertFalse(delivered.get());
        assertTrue(epochs.isCurrent(next));
    }

    @Test
    public void initialGenerationIsInactive() {
        Api102ListenerEpoch epochs = new Api102ListenerEpoch();
        assertFalse(epochs.isCurrent(0L));
        assertFalse(epochs.isCurrent(1L));
    }
}
