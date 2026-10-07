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
