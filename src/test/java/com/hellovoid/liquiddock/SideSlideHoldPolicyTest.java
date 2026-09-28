package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SideSlideHoldPolicyTest {
    @Test
    public void dwellMatchesRecoveredOs4LongClickConfirmation() {
        assertEquals(300L, SideSlideHoldPolicy.HOLD_DWELL_MS);
    }

    @Test
    public void backReadyNeverRequestsSidebar() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onReadyState("READY_STATE_BACK");
        int generation = policy.generation();

        assertFalse(policy.requestSidebar(generation));
        assertFalse(policy.shouldConsumeVendorCompletion());
    }

    @Test
    public void enteringRecentCreatesOneEligibleGeneration() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();

        assertTrue(policy.onReadyState("READY_STATE_RECENT"));
        int generation = policy.generation();
        assertFalse(policy.onReadyState("READY_STATE_RECENT"));
        assertTrue(policy.requestSidebar(generation));
        assertFalse(policy.requestSidebar(generation));
    }

    @Test
    public void desktopVisualProgressCanProvideHomeOnlyEligibility() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();

        assertTrue(policy.onDesktopProgress(true));
        int generation = policy.generation();
        assertTrue(policy.requestSidebar(generation));

        policy.onDesktopProgress(false);
        policy.onSidebarResult(true, generation);
        assertFalse(policy.shouldConsumeVendorCompletion());
    }

    @Test
    public void leavingRecentInvalidatesPendingRequest() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onReadyState("READY_STATE_RECENT");
        int generation = policy.generation();
        assertTrue(policy.requestSidebar(generation));

        policy.onReadyState("READY_STATE_BACK");
        policy.onSidebarResult(true, generation);

        assertFalse(policy.shouldConsumeVendorCompletion());
    }

    @Test
    public void onlyAcceptedCurrentRequestSuppressesVendorCompletion() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onReadyState("READY_STATE_RECENT");
        int generation = policy.generation();
        assertTrue(policy.requestSidebar(generation));

        policy.onSidebarResult(true, generation);
        assertTrue(policy.shouldConsumeVendorCompletion());
    }

    @Test
    public void unavailableSidebarFailsOpenToVendorGesture() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onReadyState("READY_STATE_RECENT");
        int generation = policy.generation();
        assertTrue(policy.requestSidebar(generation));

        policy.onSidebarResult(false, generation);
        assertFalse(policy.shouldConsumeVendorCompletion());
        assertTrue(policy.requestSidebar(generation));
    }

    @Test
    public void releaseInvalidatesLateAcknowledgement() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onReadyState("READY_STATE_RECENT");
        int generation = policy.generation();
        assertTrue(policy.requestSidebar(generation));

        policy.onUpOrCancel();
        policy.onSidebarResult(true, generation);
        assertFalse(policy.shouldConsumeVendorCompletion());
    }
}
