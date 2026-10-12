package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

/** Typed coverage; never inspect implementation source to prove runtime behavior. */
public class WorkspaceRenderEligibilityPolicyTest {
    @Test public void appCoveredHomeStopsWhileRecentsCanStillConsumeInput() {
        assertFalse(WorkspaceRenderEligibilityPolicy.renderHome(true, false));
        assertFalse(WorkspaceRenderEligibilityPolicy.normalizeInput(true, false, false));
        assertTrue(WorkspaceRenderEligibilityPolicy.pauseProducerForAppCover(true, false, false));
        assertTrue(WorkspaceRenderEligibilityPolicy.normalizeInput(true, false, true));
        assertFalse(WorkspaceRenderEligibilityPolicy.pauseProducerForAppCover(true, false, true));
        assertFalse(WorkspaceRenderEligibilityPolicy.renderHome(true, false));
    }

    @Test public void normalHomeNeverPausesOnItsOwn() {
        assertTrue(WorkspaceRenderEligibilityPolicy.renderHome(false, false));
        assertTrue(WorkspaceRenderEligibilityPolicy.normalizeInput(false, false, false));
        assertFalse(WorkspaceRenderEligibilityPolicy.pauseProducerForAppCover(false, false, false));
    }

    @Test public void workstationIsIndependentConsumerEvenWhileLauncherStops() {
        assertTrue(WorkspaceRenderEligibilityPolicy.renderHome(true, true));
        assertTrue(WorkspaceRenderEligibilityPolicy.normalizeInput(true, true, false));
        assertFalse(WorkspaceRenderEligibilityPolicy.pauseProducerForAppCover(true, true, false));
    }

    @Test public void sharingDoesNotMistakenlyEnableOccludedHomeDraws() {
        assertFalse(WorkspaceRenderEligibilityPolicy.renderHome(true, false));
        assertTrue(WorkspaceRenderEligibilityPolicy.normalizeInput(true, false, true));
    }
}
