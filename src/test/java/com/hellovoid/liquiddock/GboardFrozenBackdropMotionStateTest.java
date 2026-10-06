package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GboardFrozenBackdropMotionStateTest {
    @Test public void refreshesOnlyAtStartOfEachMovement() {
        GboardFrozenBackdropMotionState state = new GboardFrozenBackdropMotionState();

        assertFalse(state.onFrame(false));
        assertTrue(state.onFrame(true));
        assertFalse(state.onFrame(true));
        assertFalse(state.onFrame(true));

        assertFalse(state.onFrame(false));
        assertFalse(state.onFrame(false));

        assertTrue(state.onFrame(true));
        assertFalse(state.onFrame(true));
    }

    @Test public void oneStableFrameDoesNotSplitContinuousMotion() {
        GboardFrozenBackdropMotionState state = new GboardFrozenBackdropMotionState();

        assertTrue(state.onFrame(true));
        assertFalse(state.onFrame(false));
        assertFalse(state.onFrame(true));

        assertFalse(state.onFrame(false));
        assertFalse(state.onFrame(false));
        assertTrue(state.onFrame(true));
    }
}
