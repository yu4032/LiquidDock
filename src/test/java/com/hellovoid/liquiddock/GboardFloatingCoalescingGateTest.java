package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Regression coverage for drag-time frame/render coalescing. */
public class GboardFloatingCoalescingGateTest {
    @Test public void burstOfDirtySignalsSchedulesOnlyOneTask() {
        GboardFloatingCoalescingGate gate = new GboardFloatingCoalescingGate();
        assertTrue(gate.request());
        for (int i = 0; i < 99; i++) {
            assertFalse(gate.request());
        }
        assertTrue(gate.begin());
        assertFalse(gate.complete());
    }

    @Test public void updateArrivingDuringWorkRearmsExactlyOnce() {
        GboardFloatingCoalescingGate gate = new GboardFloatingCoalescingGate();
        assertTrue(gate.request());
        assertTrue(gate.begin());

        assertFalse(gate.request());
        assertFalse(gate.request());
        assertTrue(gate.complete());

        assertTrue(gate.begin());
        assertFalse(gate.complete());
        assertTrue(gate.request());
    }

    @Test public void cancelDropsPendingAndDirtyState() {
        GboardFloatingCoalescingGate gate = new GboardFloatingCoalescingGate();
        assertTrue(gate.request());
        gate.cancel();
        assertFalse(gate.begin());
        assertTrue(gate.request());
    }
}
