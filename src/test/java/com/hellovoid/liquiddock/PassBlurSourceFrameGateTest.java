package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PassBlurSourceFrameGateTest {
    @Test
    public void gateCapsOnlyObservedSourceFrames() {
        PassBlurSourceFrameGate gate = new PassBlurSourceFrameGate(30);
        int accepted = 0;
        for (int i = 0; i < 50; i++) {
            if (gate.shouldSchedule(i * 20_000_000L, 4L, 4L)) accepted++;
        }
        assertTrue("accepted=" + accepted, accepted >= 29 && accepted <= 31);
    }

    @Test
    public void freshGenerationBypassesCap() {
        PassBlurSourceFrameGate gate = new PassBlurSourceFrameGate(30);
        assertTrue(gate.shouldSchedule(0L, 1L, 1L));
        assertFalse(gate.shouldSchedule(10_000_000L, 1L, 1L));
        assertTrue(gate.shouldSchedule(10_000_000L, 1L, 2L));
        assertFalse(gate.shouldSchedule(20_000_000L, 2L, 2L));
    }
}
