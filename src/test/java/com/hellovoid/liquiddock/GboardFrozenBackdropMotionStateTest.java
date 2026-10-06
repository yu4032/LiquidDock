package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GboardFrozenBackdropMotionStateTest {
    @Test public void startupDoesNotFreezeBeforeCoordinatorAllowsIt() {
        GboardFrozenBackdropMotionState state = new GboardFrozenBackdropMotionState();

        for (int i = 0; i < 20; i++) {
            assertEquals(
                    GboardFrozenBackdropMotionState.Decision.NONE,
                    state.onFrame(false, false));
        }
        assertTrue(state.isStartup());

        for (int i = 0; i < 5; i++) {
            assertEquals(
                    GboardFrozenBackdropMotionState.Decision.NONE,
                    state.onFrame(true, false));
        }
        assertEquals(
                GboardFrozenBackdropMotionState.Decision.FREEZE_AFTER_SETTLE,
                state.onFrame(true, false));
    }

    @Test public void startupMovementRestartsStableFrameCount() {
        GboardFrozenBackdropMotionState state = new GboardFrozenBackdropMotionState();

        for (int i = 0; i < 3; i++) {
            assertEquals(
                    GboardFrozenBackdropMotionState.Decision.NONE,
                    state.onFrame(true, false));
        }
        assertEquals(
                GboardFrozenBackdropMotionState.Decision.NONE,
                state.onFrame(true, true));

        for (int i = 0; i < 5; i++) {
            assertEquals(
                    GboardFrozenBackdropMotionState.Decision.NONE,
                    state.onFrame(true, false));
        }
        assertEquals(
                GboardFrozenBackdropMotionState.Decision.FREEZE_AFTER_SETTLE,
                state.onFrame(true, false));
    }

    @Test public void frozenModeRefreshesOnlyAtStartOfEachMovement() {
        GboardFrozenBackdropMotionState state = new GboardFrozenBackdropMotionState();

        for (int i = 0; i < 6; i++) {
            GboardFrozenBackdropMotionState.Decision decision = state.onFrame(true, false);
            if (i < 5) {
                assertEquals(GboardFrozenBackdropMotionState.Decision.NONE, decision);
            } else {
                assertEquals(
                        GboardFrozenBackdropMotionState.Decision.FREEZE_AFTER_SETTLE,
                        decision);
            }
        }

        assertEquals(
                GboardFrozenBackdropMotionState.Decision.REFRESH_AT_MOTION_START,
                state.onFrame(true, true));
        assertEquals(
                GboardFrozenBackdropMotionState.Decision.NONE,
                state.onFrame(true, true));
        assertEquals(
                GboardFrozenBackdropMotionState.Decision.NONE,
                state.onFrame(true, false));
        assertEquals(
                GboardFrozenBackdropMotionState.Decision.NONE,
                state.onFrame(true, false));
        assertEquals(
                GboardFrozenBackdropMotionState.Decision.REFRESH_AT_MOTION_START,
                state.onFrame(true, true));
    }
}
