package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SideSlideHoldPolicyTest {
    @Test
    public void belowBackCompletionNeverRequestsSidebar() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown(0f, 400f, 0L);
        policy.onMove(179f, 400f, 100L);

        assertFalse(policy.shouldRequestSidebar(1000L));
        assertFalse(policy.shouldConsumeBack());
    }

    @Test
    public void completedBackButShortHoldStaysOrdinaryBack() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown(0f, 400f, 0L);
        policy.onMove(180f, 400f, 100L);

        assertFalse(policy.shouldRequestSidebar(699L));
        assertFalse(policy.shouldConsumeBack());
    }

    @Test
    public void stableCompletedBackRequestsSidebarOnceAfterDwell() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown(0f, 400f, 0L);
        policy.onMove(190f, 400f, 100L);

        assertTrue(policy.shouldRequestSidebar(700L));
        assertFalse(policy.shouldRequestSidebar(900L));

        policy.onSidebarResult(true, policy.generation());
        assertTrue(policy.shouldConsumeBack());
    }

    @Test
    public void movementOutsideStabilityWindowRestartsDwell() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown(0f, 400f, 0L);
        policy.onMove(190f, 400f, 100L);
        policy.onMove(220f, 400f, 500L);

        assertFalse(policy.shouldRequestSidebar(1000L));
        assertTrue(policy.shouldRequestSidebar(1100L));
    }

    @Test
    public void retreatBelowCompletionInvalidatesPendingHold() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown(0f, 400f, 0L);
        policy.onMove(190f, 400f, 100L);
        int generation = policy.generation();

        policy.onMove(150f, 400f, 500L);
        assertFalse(policy.shouldRequestSidebar(1200L));

        policy.onSidebarResult(true, generation);
        assertFalse(policy.shouldConsumeBack());
    }

    @Test
    public void failedOrStaleSidebarResultNeverConsumesBack() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown(0f, 400f, 0L);
        policy.onMove(190f, 400f, 100L);
        assertTrue(policy.shouldRequestSidebar(700L));
        int generation = policy.generation();

        policy.onMove(230f, 400f, 710L);
        policy.onSidebarResult(true, generation);
        assertFalse(policy.shouldConsumeBack());

        assertTrue(policy.shouldRequestSidebar(1310L));
        policy.onSidebarResult(false, policy.generation());
        assertFalse(policy.shouldConsumeBack());
    }
}
