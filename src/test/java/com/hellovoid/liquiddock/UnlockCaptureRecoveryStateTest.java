package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

/** Host-side behavior tests for the unlock freshness/capture barrier. */
public class UnlockCaptureRecoveryStateTest {
    @Test
    public void endpointChangeKeepsLiveGenerationUntilFreshnessRelease() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        state.onPrepare();
        assertEquals(4L, state.generationAfterEndpointChange(4L, 5L, false));
        state.onWorkspaceMotionStarted();

        assertEquals(4L, state.generationAfterEndpointChange(4L, 6L, false));
        assertTrue(state.isBlocked());
        assertFalse(state.isProducerBlocked());

        state.onSystemUiGoneFinished();
        assertEquals(7L, state.generationAfterEndpointChange(4L, 7L, false));
    }

    @Test
    public void rotationNeverPreservesPreviousOrientationGeneration() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        state.onPrepare();
        state.onWorkspaceMotionStarted();

        assertEquals(5L, state.generationAfterEndpointChange(4L, 5L, true));
        assertTrue(state.isBlocked());
    }

    @Test
    public void workspaceMotionAllowsProducerRebindWithoutReleasingFreshness() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        state.onPrepare();
        assertTrue(state.isProducerBlocked());

        state.onWorkspaceMotionStarted();

        assertFalse(state.isProducerBlocked());
        assertTrue(state.isBlocked());
        // A Surface replacement must still be allowed during the same unlock cycle.
        state.onPrepare();
        assertFalse(state.isProducerBlocked());
        assertTrue(state.isBlocked());
        assertTrue(state.onSystemUiGoneFinished().releaseBarrier);
    }

    @Test
    public void nextPrepareSuspendsProducerAgainAfterLiveMotion() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        state.onPrepare();
        state.onWorkspaceMotionStarted();
        state.onSystemUiGoneFinished();

        assertTrue(state.onPrepare().suspendProducers);
        assertTrue(state.isProducerBlocked());
        assertTrue(state.isBlocked());
    }

    @Test
    public void motionOutsideUnlockCannotOpenNextProducerGate() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        state.onWorkspaceMotionStarted();
        assertFalse(state.isProducerBlocked());

        state.onPrepare();

        assertTrue(state.isProducerBlocked());
        assertTrue(state.isBlocked());
    }

    @Test
    public void staleTimeoutKeepsNewPrepareProducerSuspended() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        long first = state.onPrepare().serial;
        state.onWorkspaceMotionStarted();
        assertTrue(state.onBarrierTimeout(first).releaseBarrier);
        assertFalse(state.isProducerBlocked());
        state.onPrepare();

        assertFalse(state.onBarrierTimeout(first).releaseBarrier);
        assertTrue(state.isProducerBlocked());
        assertTrue(state.isBlocked());
    }

    @Test
    public void prepareArmsCaptureAndSuspendsOnce() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();

        UnlockCaptureRecoveryState.Decision decision = state.onPrepare();

        assertTrue(state.isBlocked());
        assertTrue(decision.suspendProducers);
        assertFalse(decision.releaseBarrier);
        assertTrue(decision.serial > 0L);
    }

    @Test
    public void duplicatePreparePreservesActiveSerialWithoutRepause() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        UnlockCaptureRecoveryState.Decision first = state.onPrepare();

        UnlockCaptureRecoveryState.Decision duplicate = state.onPrepare();

        assertEquals(first.serial, duplicate.serial);
        assertFalse(duplicate.suspendProducers);
        assertFalse(duplicate.releaseBarrier);
        assertTrue(state.isBlocked());
    }

    @Test
    public void systemUiGoneReleasesBarrierImmediately() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        UnlockCaptureRecoveryState.Decision prepare = state.onPrepare();

        UnlockCaptureRecoveryState.Decision gone = state.onSystemUiGoneFinished();

        assertEquals(prepare.serial, gone.serial);
        assertTrue(gone.releaseBarrier);
        assertFalse(state.isBlocked());
    }

    @Test
    public void nextUnlockGetsNewSerialAfterGoneRelease() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        UnlockCaptureRecoveryState.Decision first = state.onPrepare();
        state.onSystemUiGoneFinished();

        UnlockCaptureRecoveryState.Decision second = state.onPrepare();

        assertNotEquals(first.serial, second.serial);
        assertTrue(second.suspendProducers);
        assertTrue(state.isBlocked());
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
    public void skippedPrepareNeedsNoSyntheticFreezeAtGone() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();

        UnlockCaptureRecoveryState.Decision gone = state.onSystemUiGoneFinished();

        assertFalse(gone.suspendProducers);
        assertFalse(gone.releaseBarrier);
        assertFalse(state.isBlocked());
    }

    @Test
    public void duplicateGoneIsIdempotent() {
        UnlockCaptureRecoveryState state = new UnlockCaptureRecoveryState();
        state.onPrepare();
        UnlockCaptureRecoveryState.Decision first = state.onSystemUiGoneFinished();
        UnlockCaptureRecoveryState.Decision duplicate = state.onSystemUiGoneFinished();

        assertTrue(first.releaseBarrier);
        assertFalse(duplicate.releaseBarrier);
        assertFalse(state.isBlocked());
    }
}
