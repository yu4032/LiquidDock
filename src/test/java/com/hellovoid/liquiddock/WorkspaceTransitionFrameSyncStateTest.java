package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorkspaceTransitionFrameSyncStateTest {
    @Test
    public void firstMotionEnablesAndContinuousMotionKeepsLease() {
        WorkspaceTransitionFrameSyncState state = new WorkspaceTransitionFrameSyncState();

        WorkspaceTransitionFrameSyncState.Decision first = state.onPreDraw(true);
        assertTrue(first.enable);
        assertFalse(first.disable);
        assertTrue(state.isActive());

        WorkspaceTransitionFrameSyncState.Decision next = state.onPreDraw(true);
        assertFalse(next.enable);
        assertFalse(next.disable);
        assertTrue(state.isActive());
    }

    @Test
    public void twoStablePredrawsReleaseButOneDoesNot() {
        WorkspaceTransitionFrameSyncState state = new WorkspaceTransitionFrameSyncState();
        state.onPreDraw(true);

        WorkspaceTransitionFrameSyncState.Decision firstStable = state.onPreDraw(false);
        assertFalse(firstStable.disable);
        assertTrue(state.isActive());

        WorkspaceTransitionFrameSyncState.Decision secondStable = state.onPreDraw(false);
        assertTrue(secondStable.disable);
        assertFalse(state.isActive());
    }

    @Test
    public void renewedMotionCancelsPendingRelease() {
        WorkspaceTransitionFrameSyncState state = new WorkspaceTransitionFrameSyncState();
        state.onPreDraw(true);
        state.onPreDraw(false);
        state.onPreDraw(true);

        WorkspaceTransitionFrameSyncState.Decision stableAgain = state.onPreDraw(false);
        assertFalse(stableAgain.disable);
        assertTrue(state.isActive());
    }

    @Test
    public void motionCanReenableAfterStableRelease() {
        WorkspaceTransitionFrameSyncState state = new WorkspaceTransitionFrameSyncState();
        state.onPreDraw(true);
        state.onPreDraw(false);
        assertTrue(state.onPreDraw(false).disable);
        assertFalse(state.isActive());

        WorkspaceTransitionFrameSyncState.Decision nextMotion = state.onPreDraw(true);
        assertTrue(nextMotion.enable);
        assertTrue(state.isActive());
    }

    @Test
    public void resetReleasesOnlyAnActiveLease() {
        WorkspaceTransitionFrameSyncState state = new WorkspaceTransitionFrameSyncState();
        assertFalse(state.reset().disable);

        state.onPreDraw(true);
        assertTrue(state.reset().disable);
        assertFalse(state.isActive());
    }
}
