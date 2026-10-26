package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Api102ReloadGateTest {
    @Test public void ownerBlocksHandoffEvenAfterPartialBootstrap() {
        Api102ReloadGate gate = new Api102ReloadGate();
        assertTrue(gate.enterOwnerLifecycle());
        assertFalse(gate.beginHandoff());
        assertTrue(gate.enterOwnerLifecycle());
    }

    @Test public void acceptedHandoffBlocksLatePackageReady() {
        Api102ReloadGate gate = new Api102ReloadGate();
        assertTrue(gate.beginHandoff());
        assertFalse(gate.enterOwnerLifecycle());
        assertFalse(gate.beginHandoff());
    }

    @Test public void failedStateTransferReturnsToPreInstall() {
        Api102ReloadGate gate = new Api102ReloadGate();
        assertTrue(gate.beginHandoff());
        gate.abortHandoff();
        assertTrue(gate.enterOwnerLifecycle());
        assertFalse(gate.beginHandoff());
    }

    @Test public void concurrentOwnerAndHandoffCannotBothWin() throws Exception {
        Api102ReloadGate gate = new Api102ReloadGate();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> owner = pool.submit(() -> { start.await(); return gate.enterOwnerLifecycle(); });
            Future<Boolean> handoff = pool.submit(() -> { start.await(); return gate.beginHandoff(); });
            start.countDown();
            assertTrue(owner.get() ^ handoff.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
