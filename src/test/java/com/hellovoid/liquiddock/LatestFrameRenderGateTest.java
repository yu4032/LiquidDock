package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LatestFrameRenderGateTest {
    @Test
    public void repeatedSignalsBeforeRenderCollapseIntoOneOwner() {
        LatestFrameRenderGate gate = new LatestFrameRenderGate();

        assertTrue(gate.request());
        assertFalse(gate.request());
        assertFalse(gate.request());

        gate.beginRender();
        assertFalse(gate.finishRenderAndNeedsFollowUp());
    }

    @Test
    public void signalDuringRenderProducesExactlyOneFollowUp() {
        LatestFrameRenderGate gate = new LatestFrameRenderGate();

        assertTrue(gate.request());
        gate.beginRender();

        assertFalse(gate.request());
        assertFalse(gate.request());

        assertTrue(gate.finishRenderAndNeedsFollowUp());

        gate.beginRender();
        assertFalse(gate.finishRenderAndNeedsFollowUp());
    }

    @Test
    public void racingNewOwnerPreventsDuplicateFollowUp() {
        LatestFrameRenderGate gate = new LatestFrameRenderGate();

        assertTrue(gate.request());
        gate.beginRender();

        assertFalse(gate.request());
        assertTrue(gate.finishRenderAndNeedsFollowUp());

        // A new render is already owned; more signals do not create another owner.
        assertFalse(gate.request());
    }
}
