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
    public void belowSecondStageNeverArmsSidebar() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onSecondStageProgress(false);
        int generation = policy.generation();

        assertFalse(policy.requestArm(generation));
        assertFalse(policy.shouldConsumeVendorCompletion());
    }

    @Test
    public void enteringSecondStageCreatesOneArmOpportunity() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();

        assertTrue(policy.onSecondStageProgress(true));
        int generation = policy.generation();
        assertFalse(policy.onSecondStageProgress(true));
        assertTrue(policy.requestArm(generation));
        assertFalse(policy.requestArm(generation));
    }

    @Test
    public void successfulDwellOnlyArmsUntilRelease() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onSecondStageProgress(true);
        int generation = policy.generation();

        assertTrue(policy.requestArm(generation));
        assertTrue(policy.onArmResult(true, generation));
        assertTrue(policy.isArmed());
        assertFalse(policy.shouldConsumeVendorCompletion());

        assertTrue(policy.commitRelease(generation));
        assertTrue(policy.shouldConsumeVendorCompletion());
    }

    @Test
    public void leavingEligibleStateCancelsArm() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onSecondStageProgress(true);
        int generation = policy.generation();
        assertTrue(policy.requestArm(generation));
        assertTrue(policy.onArmResult(true, generation));

        policy.onSecondStageProgress(false);

        assertFalse(policy.isArmed());
        assertFalse(policy.commitRelease(generation));
        assertFalse(policy.shouldConsumeVendorCompletion());
    }

    @Test
    public void unavailablePreflightFailsOpen() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onSecondStageProgress(true);
        int generation = policy.generation();

        assertTrue(policy.requestArm(generation));
        assertFalse(policy.onArmResult(false, generation));
        assertFalse(policy.isArmed());
        assertFalse(policy.commitRelease(generation));
        assertFalse(policy.shouldConsumeVendorCompletion());
    }

    @Test
    public void finishInvalidatesArmedGesture() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onSecondStageProgress(true);
        int generation = policy.generation();
        assertTrue(policy.requestArm(generation));
        assertTrue(policy.onArmResult(true, generation));

        policy.onFinish();

        assertFalse(policy.commitRelease(generation));
        assertFalse(policy.shouldConsumeVendorCompletion());
    }
}
