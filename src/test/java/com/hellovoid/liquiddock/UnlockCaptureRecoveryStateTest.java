package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
