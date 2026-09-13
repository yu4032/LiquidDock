package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Dock backdrop sampling must consume every vendor motion update before VSYNC-loop coalescing. */
public class DockBackdropMotionSyncContractTest {
    @Test
    public void firstVendorMotionFrameRefreshesNowAndSchedulesContinuation() {
        DockBackdropMotionSyncState state = new DockBackdropMotionSyncState();

        DockBackdropMotionSyncState.Decision decision = state.onVendorMotionFrame();

        assertTrue(decision.refreshMappingNow);
        assertTrue(decision.scheduleContinuation);
    }

    @Test
    public void laterVendorMotionFrameStillRefreshesNowWhenContinuationAlreadyPending() {
        DockBackdropMotionSyncState state = new DockBackdropMotionSyncState();
        state.onVendorMotionFrame();

        DockBackdropMotionSyncState.Decision decision = state.onVendorMotionFrame();

        assertTrue("coalescing the continuation must never suppress current-frame mapping",
                decision.refreshMappingNow);
        assertFalse(decision.scheduleContinuation);
    }

    @Test
    public void continuationVsyncReopensOneSchedulingSlot() {
        DockBackdropMotionSyncState state = new DockBackdropMotionSyncState();
        state.onVendorMotionFrame();

        state.onContinuationVsync();
        DockBackdropMotionSyncState.Decision next = state.onVendorMotionFrame();

        assertTrue(next.refreshMappingNow);
        assertTrue(next.scheduleContinuation);
    }

    @Test
    public void resetDropsPendingContinuation() {
        DockBackdropMotionSyncState state = new DockBackdropMotionSyncState();
        state.onVendorMotionFrame();

        state.reset();
        DockBackdropMotionSyncState.Decision next = state.onVendorMotionFrame();

        assertTrue(next.refreshMappingNow);
        assertTrue(next.scheduleContinuation);
    }
}
