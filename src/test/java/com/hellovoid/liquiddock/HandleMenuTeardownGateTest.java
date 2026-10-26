package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
}
