package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

/** Host-side behavior tests for fail-closed unlock producer recovery. */
public class UnlockCaptureRecoveryStateTest {
    @Test
    public void prepareArmsCaptureAndSuspendsOnce() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();

        UnlockCaptureRecoveryState.Decision decision = state.onPrepare();

        assertTrue(state.isBlocked());
        assertTrue(decision.suspendProducers);
        assertFalse(decision.requestRollover);
        assertFalse(decision.releaseBarrier);
        assertTrue(decision.serial > 0L);
    }

    @Test
    public void duplicatePreparePreservesInFlightRolloverSerial() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        UnlockCaptureRecoveryState.Decision first = state.onPrepare();
        UnlockCaptureRecoveryState.Decision request = state.onSystemUiGoneFinished();

        UnlockCaptureRecoveryState.Decision duplicate = state.onPrepare();

        assertEquals(first.serial, duplicate.serial);
        assertEquals(request.serial, duplicate.serial);
        assertFalse(duplicate.suspendProducers);
        assertFalse(duplicate.requestRollover);
        assertFalse(duplicate.releaseBarrier);

        UnlockCaptureRecoveryState.Decision complete =
                state.onRolloverFinished(request.serial, true);
        assertTrue(complete.releaseBarrier);
        assertFalse(state.isBlocked());
    }

    @Test
    public void failedRolloverRecoversOnNextUnlock() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        UnlockCaptureRecoveryState.Decision firstPrepare = state.onPrepare();
        UnlockCaptureRecoveryState.Decision firstRequest = state.onSystemUiGoneFinished();

        UnlockCaptureRecoveryState.Decision failed =
                state.onRolloverFinished(firstRequest.serial, false);
        assertFalse(failed.releaseBarrier);
        assertTrue(state.isBlocked());

        UnlockCaptureRecoveryState.Decision secondPrepare = state.onPrepare();
        assertTrue(secondPrepare.suspendProducers);
        assertFalse(secondPrepare.requestRollover);
        assertFalse(secondPrepare.releaseBarrier);
        assertNotEquals(firstPrepare.serial, secondPrepare.serial);

        UnlockCaptureRecoveryState.Decision secondRequest = state.onSystemUiGoneFinished();
        assertEquals(secondPrepare.serial, secondRequest.serial);
        assertTrue(secondRequest.requestRollover);
        assertFalse(secondRequest.releaseBarrier);

        UnlockCaptureRecoveryState.Decision complete =
                state.onRolloverFinished(secondRequest.serial, true);
        assertTrue(complete.releaseBarrier);
        assertFalse(state.isBlocked());

        UnlockCaptureRecoveryState.Decision stale =
                state.onRolloverFinished(firstRequest.serial, true);
        assertFalse(stale.releaseBarrier);
        assertFalse(state.isBlocked());
    }

    @Test
    public void currentBarrierTimeoutFailsOpenButStaleTimeoutCannotReleaseNewCycle() throws Exception {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        Method timeout = UnlockCaptureRecoveryState.class.getDeclaredMethod(
                "onBarrierTimeout", long.class);
        timeout.setAccessible(true);

        UnlockCaptureRecoveryState.Decision first = state.onPrepare();
        UnlockCaptureRecoveryState.Decision timedOut =
                (UnlockCaptureRecoveryState.Decision) timeout.invoke(state, first.serial);
        assertTrue(timedOut.releaseBarrier);
        assertFalse(state.isBlocked());

        UnlockCaptureRecoveryState.Decision second = state.onPrepare();
        state.onSystemUiGoneFinished();
        UnlockCaptureRecoveryState.Decision stale =
                (UnlockCaptureRecoveryState.Decision) timeout.invoke(state, first.serial);
        assertFalse(stale.releaseBarrier);
        assertTrue(state.isBlocked());

        UnlockCaptureRecoveryState.Decision current =
                (UnlockCaptureRecoveryState.Decision) timeout.invoke(state, second.serial);
        assertTrue(current.releaseBarrier);
        assertFalse(state.isBlocked());
    }

    @Test
    public void rolloverCompletionIsNotFreshness() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        state.onPrepare();

        UnlockCaptureRecoveryState.Decision request = state.onSystemUiGoneFinished();
        assertTrue(request.requestRollover);
        assertFalse(request.releaseBarrier);

        UnlockCaptureRecoveryState.Decision rolled =
                state.onRolloverFinished(request.serial, true);
        assertTrue(rolled.releaseBarrier);
        assertFalse(state.isBlocked());
    }

    @Test
    public void rejectionRemainsFailClosedAndStaleCompletionCannotRelease() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        UnlockCaptureRecoveryState.Decision first = state.onPrepare();
        UnlockCaptureRecoveryState.Decision request = state.onSystemUiGoneFinished();

        UnlockCaptureRecoveryState.Decision rejected =
                state.onRolloverFinished(request.serial, false);
        assertFalse(rejected.releaseBarrier);
        assertTrue(state.isBlocked());

        UnlockCaptureRecoveryState.Decision stale =
                state.onRolloverFinished(first.serial - 1L, true);
        assertFalse(stale.releaseBarrier);
        assertTrue(state.isBlocked());
    }

    @Test
    public void skippedPrepareFailsClosedBeforeRequestingRollover() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();

        UnlockCaptureRecoveryState.Decision request = state.onSystemUiGoneFinished();

        assertTrue(state.isBlocked());
        assertTrue(request.suspendProducers);
        assertTrue(request.requestRollover);
        assertFalse(request.releaseBarrier);
        assertTrue(request.serial > 0L);
    }

    @Test
    public void duplicateGoneFinishedDoesNotQueueDuplicateRollover() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        state.onPrepare();
        UnlockCaptureRecoveryState.Decision first = state.onSystemUiGoneFinished();

        UnlockCaptureRecoveryState.Decision duplicate = state.onSystemUiGoneFinished();

        assertTrue(first.requestRollover);
        assertFalse(duplicate.suspendProducers);
        assertFalse(duplicate.requestRollover);
        assertFalse(duplicate.releaseBarrier);
        assertTrue(state.isBlocked());
    }
}
