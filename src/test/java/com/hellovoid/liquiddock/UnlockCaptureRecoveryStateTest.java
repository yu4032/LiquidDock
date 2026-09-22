package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Host-side behavior tests for fail-closed unlock producer recovery. */
public class UnlockCaptureRecoveryStateTest {
    @Test
    public void prepareArmsCaptureAndSuspends() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();

        UnlockCaptureRecoveryState.Decision decision = state.onPrepare();

        assertTrue(state.isBlocked());
        assertEquals(UnlockCaptureRecoveryState.Phase.BLOCKED_WAITING_GONE, state.phase());
        assertTrue(decision.suspendProducers);
        assertFalse(decision.requestRollover);
        assertFalse(decision.releaseBarrier);
        assertFalse(decision.failedClosed);
        assertTrue(decision.serial > 0L);
    }

    @Test
    public void goneFinishedRequestsRolloverExactlyOnce() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        UnlockCaptureRecoveryState.Decision prepare = state.onPrepare();

        UnlockCaptureRecoveryState.Decision first = state.onSystemUiGoneFinished();
        UnlockCaptureRecoveryState.Decision duplicate = state.onSystemUiGoneFinished();

        assertEquals(prepare.serial, first.serial);
        assertTrue(first.requestRollover);
        assertEquals(UnlockCaptureRecoveryState.Phase.ROLLING_OVER, state.phase());
        assertFalse(duplicate.suspendProducers);
        assertFalse(duplicate.requestRollover);
        assertFalse(duplicate.releaseBarrier);
        assertTrue(state.isBlocked());
    }

    @Test
    public void successfulRolloverIsOnlyRecoveryAuthority() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        state.onPrepare();
        UnlockCaptureRecoveryState.Decision request = state.onSystemUiGoneFinished();

        UnlockCaptureRecoveryState.Decision complete =
                state.onRolloverFinished(request.serial, true);

        assertTrue(complete.releaseBarrier);
        assertFalse(complete.failedClosed);
        assertFalse(state.isBlocked());
        assertEquals(UnlockCaptureRecoveryState.Phase.RECOVERED, state.phase());
    }

    @Test
    public void failedRolloverRemainsFailedClosed() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        state.onPrepare();
        UnlockCaptureRecoveryState.Decision request = state.onSystemUiGoneFinished();

        UnlockCaptureRecoveryState.Decision failed =
                state.onRolloverFinished(request.serial, false);

        assertFalse(failed.releaseBarrier);
        assertTrue(failed.failedClosed);
        assertTrue(state.isBlocked());
        assertEquals(UnlockCaptureRecoveryState.Phase.FAILED_CLOSED, state.phase());

        UnlockCaptureRecoveryState.Decision repeatedFinished = state.onSystemUiGoneFinished();
        assertFalse(repeatedFinished.requestRollover);
        assertFalse(repeatedFinished.releaseBarrier);
        assertTrue(repeatedFinished.failedClosed);
    }

    @Test
    public void watchdogCanOnlyFailClosedNeverRecover() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        UnlockCaptureRecoveryState.Decision prepare = state.onPrepare();

        UnlockCaptureRecoveryState.Decision timeout =
                state.onWatchdogTimeout(prepare.serial);

        assertFalse(timeout.releaseBarrier);
        assertTrue(timeout.failedClosed);
        assertTrue(state.isBlocked());
        assertEquals(UnlockCaptureRecoveryState.Phase.FAILED_CLOSED, state.phase());
    }

    @Test
    public void failedCycleCanRetryOnlyFromNewPrepare() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        UnlockCaptureRecoveryState.Decision firstPrepare = state.onPrepare();
        UnlockCaptureRecoveryState.Decision firstRequest = state.onSystemUiGoneFinished();
        state.onRolloverFinished(firstRequest.serial, false);

        UnlockCaptureRecoveryState.Decision secondPrepare = state.onPrepare();

        assertNotEquals(firstPrepare.serial, secondPrepare.serial);
        assertTrue(secondPrepare.suspendProducers);
        assertEquals(UnlockCaptureRecoveryState.Phase.BLOCKED_WAITING_GONE, state.phase());

        UnlockCaptureRecoveryState.Decision secondRequest = state.onSystemUiGoneFinished();
        assertEquals(secondPrepare.serial, secondRequest.serial);
        assertTrue(secondRequest.requestRollover);

        UnlockCaptureRecoveryState.Decision complete =
                state.onRolloverFinished(secondRequest.serial, true);
        assertTrue(complete.releaseBarrier);
        assertFalse(state.isBlocked());
    }

    @Test
    public void newerPrepareInvalidatesOlderCompletion() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        UnlockCaptureRecoveryState.Decision prepareA = state.onPrepare();
        UnlockCaptureRecoveryState.Decision requestA = state.onSystemUiGoneFinished();
        assertEquals(prepareA.serial, requestA.serial);

        UnlockCaptureRecoveryState.Decision prepareB = state.onPrepare();
        assertNotEquals(prepareA.serial, prepareB.serial);

        UnlockCaptureRecoveryState.Decision stale =
                state.onRolloverFinished(requestA.serial, true);

        assertFalse(stale.releaseBarrier);
        assertTrue(state.isBlocked());
        assertEquals(UnlockCaptureRecoveryState.Phase.BLOCKED_WAITING_GONE, state.phase());

        UnlockCaptureRecoveryState.Decision requestB = state.onSystemUiGoneFinished();
        UnlockCaptureRecoveryState.Decision completeB =
                state.onRolloverFinished(requestB.serial, true);
        assertTrue(completeB.releaseBarrier);
        assertFalse(state.isBlocked());
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
    public void staleWatchdogCannotFailNewCycle() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        long first = state.onPrepare().serial;
        long second = state.onPrepare().serial;

        UnlockCaptureRecoveryState.Decision stale = state.onWatchdogTimeout(first);

        assertEquals(second, state.activeSerial());
        assertFalse(stale.releaseBarrier);
        assertFalse(stale.failedClosed);
        assertEquals(UnlockCaptureRecoveryState.Phase.BLOCKED_WAITING_GONE, state.phase());
    }
}
