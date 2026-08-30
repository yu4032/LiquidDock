package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DockWorkstationVisualTransitionTest {
    @Test public void exitHoldsStrokeUntilCurrentGenerationSettles() {
        DockWorkstationVisualTransition state = new DockWorkstationVisualTransition();
        assertTrue(state.shouldCommitStrokeGeometry());

        state.onModeChanged(true);
        assertFalse(state.shouldCommitStrokeGeometry());

        int exitGeneration = state.onModeChanged(false);
        assertFalse(state.shouldCommitStrokeGeometry());
        assertFalse(state.settleExit(exitGeneration - 1));
        assertFalse(state.shouldCommitStrokeGeometry());

        assertTrue(state.settleExit(exitGeneration));
        assertTrue(state.shouldCommitStrokeGeometry());
    }

    @Test public void rapidReentryInvalidatesPendingExitSettlement() {
        DockWorkstationVisualTransition state = new DockWorkstationVisualTransition();
        state.onModeChanged(true);
        int exitGeneration = state.onModeChanged(false);
        state.onModeChanged(true);

        assertFalse(state.settleExit(exitGeneration));
        assertFalse(state.shouldCommitStrokeGeometry());
    }
}
