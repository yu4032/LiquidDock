package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class PassBlurClaimSubmissionGuardTest {
    @Test public void firstSubmissionOnlyIsForwarded() {
        PassBlurClaimSubmissionGuard guard = new PassBlurClaimSubmissionGuard();
        assertTrue(guard.shouldSuppress(false, false));  // vendor before first bind
        assertFalse(guard.shouldSuppress(false, true));  // module producer first bind
        assertTrue(guard.shouldSuppress(false, true));   // same IGBP must not be re-parceled
        assertTrue(guard.shouldSuppress(false, false));  // vendor override suppressed
    }

    @Test public void onlyExplicitModuleDetachMaySendNull() {
        PassBlurClaimSubmissionGuard guard = new PassBlurClaimSubmissionGuard();
        assertFalse(guard.shouldSuppress(false, true));
        assertTrue(guard.shouldSuppress(false, false));  // vendor null cannot detach us
        assertFalse(guard.shouldSuppress(true, false));  // our real native detach
        assertTrue(guard.shouldSuppress(false, true));   // detach does not reset parcel history
    }

    @Test public void concurrentSubmissionsAdmitAtMostOne() throws Exception {
        PassBlurClaimSubmissionGuard guard = new PassBlurClaimSubmissionGuard();
        var pool = Executors.newFixedThreadPool(8);
        CountDownLatch gate = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 32; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        gate.await();
                        if (!guard.shouldSuppress(false, true)) admitted.incrementAndGet();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            gate.countDown();
            for (var f : futures) f.get(5, TimeUnit.SECONDS);
            assertTrue(admitted.get() == 1);
        } finally {
            pool.shutdownNow();
        }
    }
}
