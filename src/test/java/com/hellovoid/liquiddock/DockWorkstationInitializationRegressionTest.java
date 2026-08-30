package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Device regression: Launcher 4.50 emits onLaptopModeChanged(false) during normal startup. */
public class DockWorkstationInitializationRegressionTest {
    @Test public void redundantNormalModeSignalDoesNotCreateFakeExitTransition() {
        DockWorkstationVisualTransition state = new DockWorkstationVisualTransition();
        state.initialize(false);
        int generation = state.generation();

        state.onModeChanged(false);

        assertFalse(state.isExiting());
        assertTrue(state.shouldCommitStrokeGeometry());
        assertEquals(generation, state.generation());
    }

    @Test public void realWorkstationExitStillCreatesASettlementGeneration() {
        DockWorkstationVisualTransition state = new DockWorkstationVisualTransition();
        state.initialize(false);
        int enterGeneration = state.onModeChanged(true);
        int exitGeneration = state.onModeChanged(false);

        assertTrue(state.isExiting());
        assertTrue(exitGeneration > enterGeneration);
    }
}
