package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Runtime contract matching Launcher's latest-geometry output scheduling. */
public class GboardFloatingFrameRenderGateTest {
    @Test public void burstPublishesQueueOnlyOneRenderDrainAndKeepLatestRevision() {
        GboardFloatingFrameRenderGate gate = new GboardFloatingFrameRenderGate();

        assertTrue(gate.publish(1L));
        for (long revision = 2L; revision <= 100L; revision++) {
            assertFalse(gate.publish(revision));
        }

        assertEquals(100L, gate.beginDrain());
        assertEquals(-1L, gate.nextOrIdle());
        assertFalse(gate.isQueued());
    }

    @Test public void updatesDuringDrawAreConsumedBySameDrainWithoutPostingAnotherTask() {
        GboardFloatingFrameRenderGate gate = new GboardFloatingFrameRenderGate();

        assertTrue(gate.publish(10L));
        assertEquals(10L, gate.beginDrain());
        assertFalse(gate.publish(11L));
        assertFalse(gate.publish(12L));
        assertEquals(12L, gate.nextOrIdle());
        assertEquals(-1L, gate.nextOrIdle());
        assertFalse(gate.isQueued());
    }

    @Test public void cancelDropsPendingWork() {
        GboardFloatingFrameRenderGate gate = new GboardFloatingFrameRenderGate();

        assertTrue(gate.publish(1L));
        gate.cancel();

        assertEquals(-1L, gate.beginDrain());
        assertFalse(gate.isQueued());
    }
}
