package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LatestFrameRenderGateTest {
    @Test
    public void collapsesQueuedRequestsAndPreservesProducerIntent() {
        LatestFrameRenderGate gate = new LatestFrameRenderGate();

        assertTrue(gate.request(false));
        assertFalse(gate.request(true));
        assertFalse(gate.request(false));
        assertTrue(gate.beginRun());
        assertFalse(gate.finishRunAndClaimFollowUp());
        assertEquals(2L, gate.takeCoalescedRequestCount());
    }

    @Test
    public void requestDuringRunBecomesExactlyOneFollowUp() {
        LatestFrameRenderGate gate = new LatestFrameRenderGate();

        assertTrue(gate.request(true));
        assertTrue(gate.beginRun());

        assertFalse(gate.request(false));
        assertFalse(gate.request(true));

        assertTrue(gate.finishRunAndClaimFollowUp());
        assertTrue(gate.beginRun());
        assertFalse(gate.finishRunAndClaimFollowUp());
        assertEquals(2L, gate.takeCoalescedRequestCount());
    }

    @Test
    public void sceneOnlyRunDoesNotPretendToConsumeProducerSignal() {
        LatestFrameRenderGate gate = new LatestFrameRenderGate();

        assertTrue(gate.request(false));
        assertFalse(gate.beginRun());
        assertFalse(gate.finishRunAndClaimFollowUp());
    }
}
