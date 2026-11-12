package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

/** Pure-JVM lifecycle tests; no Android or native SurfaceControl required. */
public class HandleMenuTeardownGateTest {
    @Test
    public void failedNativeDetachCannotScheduleProducerDestruction() {
        HandleMenuTeardownGate gate = new HandleMenuTeardownGate();
        AtomicInteger attempts = new AtomicInteger();
        assertFalse(gate.attemptDetach(() -> {
            attempts.incrementAndGet();
            return false;
        }));
        assertFalse(gate.isDetached());
        assertFalse(gate.claimRenderRelease());
        assertEquals(1, gate.failedDetachAttempts());

        assertTrue(gate.attemptDetach(() -> {
            attempts.incrementAndGet();
            return true;
        }));
        assertTrue(gate.isDetached());
        assertEquals(2, attempts.get());
        assertTrue(gate.claimRenderRelease());
        assertFalse(gate.claimRenderRelease()); // duplicate shutdown cannot double-release
    }

    @Test
    public void throwingDetachRemainsRetryableAndDoesNotClaimSuccess() {
        HandleMenuTeardownGate gate = new HandleMenuTeardownGate();
        assertFalse(gate.attemptDetach(() -> {
            throw new IllegalStateException("native transaction rejected");
        }));
        assertEquals(1, gate.failedDetachAttempts());
        assertFalse(gate.claimRenderRelease());
        assertTrue(gate.attemptDetach(() -> true));
        assertEquals(1, gate.failedDetachAttempts());
        assertTrue(gate.claimRenderRelease());
    }

    @Test
    public void rejectedRenderPostCanBeRescheduledWithoutRebinding() {
        HandleMenuTeardownGate gate = new HandleMenuTeardownGate();
        AtomicInteger detachCalls = new AtomicInteger();
        assertTrue(gate.attemptDetach(() -> {
            detachCalls.incrementAndGet();
            return true;
        }));
        assertTrue(gate.claimRenderRelease());
        gate.renderPostRejected();
        assertTrue(gate.attemptDetach(() -> {
            detachCalls.incrementAndGet();
            return false;
        }));
        assertEquals(1, detachCalls.get());
        assertTrue(gate.claimRenderRelease());
    }

    @Test
    public void concurrentShutdownAttemptsDetachOnlyOnce() throws Exception {
        HandleMenuTeardownGate gate = new HandleMenuTeardownGate();
        AtomicInteger detachCalls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> action = () -> {
                start.await();
                return gate.attemptDetach(() -> {
                    detachCalls.incrementAndGet();
                    return true;
                });
            };
            Future<Boolean> first = executor.submit(action);
            Future<Boolean> second = executor.submit(action);
            start.countDown();
            assertTrue(first.get(3, TimeUnit.SECONDS));
            assertTrue(second.get(3, TimeUnit.SECONDS));
            assertEquals(1, detachCalls.get());
            assertTrue(gate.claimRenderRelease());
            assertFalse(gate.claimRenderRelease());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void delayedNativeCommitKeepsProducerAliveUntilCallbackObserved() {
        HandleMenuTeardownGate gate = new HandleMenuTeardownGate();
        AtomicBoolean committed = new AtomicBoolean(false);
        AtomicInteger releases = new AtomicInteger();

        // Transaction.apply has returned, but the native committed callback has not.
        assertFalse(gate.attemptDetach(committed::get));
        assertFalse(gate.claimRenderRelease());
        assertEquals(0, releases.get());

        // A completion listener fires later; only then may the main-thread retry proceed.
        committed.set(true);
        assertTrue(gate.attemptDetach(committed::get));
        if (gate.claimRenderRelease()) releases.incrementAndGet();
        if (gate.claimRenderRelease()) releases.incrementAndGet();
        assertEquals(1, releases.get());
    }

    @Test
    public void failingCompletionCallbackCannotSuppressOtherOwnerCleanup() {
        AtomicInteger delivered = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        PassBlurDetachCallbacks.dispatch(Arrays.asList(
                () -> { throw new IllegalStateException("callback failed"); },
                delivered::incrementAndGet,
                () -> { throw new AssertionError("bad diagnostic route"); },
                delivered::incrementAndGet),
                error -> {
                    failures.incrementAndGet();
                    // Even logging itself may fail; remaining callbacks must still run.
                    throw new IllegalStateException("log rejected");
                });
        assertEquals(2, delivered.get());
        assertEquals(2, failures.get());
    }

    @Test
    public void concurrentCloseWhileCommitPendingCannotReleaseEarly() throws Exception {
        HandleMenuTeardownGate gate = new HandleMenuTeardownGate();
        AtomicBoolean committed = new AtomicBoolean(false);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> action = () -> {
                start.await();
                boolean result = gate.attemptDetach(committed::get);
                assertFalse(gate.claimRenderRelease());
                return result;
            };
            Future<Boolean> first = executor.submit(action);
            Future<Boolean> second = executor.submit(action);
            start.countDown();
            assertFalse(first.get(3, TimeUnit.SECONDS));
            assertFalse(second.get(3, TimeUnit.SECONDS));
            committed.set(true);
            assertTrue(gate.attemptDetach(committed::get));
            assertTrue(gate.claimRenderRelease());
            assertFalse(gate.claimRenderRelease());
        } finally {
            executor.shutdownNow();
        }
    }
}
