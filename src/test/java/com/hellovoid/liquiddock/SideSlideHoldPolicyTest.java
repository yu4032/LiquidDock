package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class SideSlideHoldPolicyTest {
    @Test
    public void dwellMatchesRecoveredOs4LongClickConfirmation() {
        assertEquals(300L, SideSlideHoldPolicy.HOLD_DWELL_MS);
    }

    @Test
    public void backReadyNeverArmsSidebar() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onReadyState("READY_STATE_BACK");
        int generation = policy.generation();

        assertFalse(policy.requestArm(generation));
        assertFalse(policy.shouldConsumeVendorCompletion());
    }

    @Test
    public void enteringRecentCreatesOneArmOpportunity() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();

        assertTrue(policy.onReadyState("READY_STATE_RECENT"));
        int generation = policy.generation();
        assertFalse(policy.onReadyState("READY_STATE_RECENT"));
        assertTrue(policy.requestArm(generation));
        assertFalse(policy.requestArm(generation));
    }

    @Test
    public void successfulDwellOnlyArmsUntilRelease() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onReadyState("READY_STATE_RECENT");
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
        policy.onReadyState("READY_STATE_RECENT");
        int generation = policy.generation();
        assertTrue(policy.requestArm(generation));
        assertTrue(policy.onArmResult(true, generation));

        policy.onReadyState("READY_STATE_BACK");

        assertFalse(policy.isArmed());
        assertFalse(policy.commitRelease(generation));
        assertFalse(policy.shouldConsumeVendorCompletion());
    }

    @Test
    public void unifiedHookKeepsSecondStageAndHasNoSceneSpecificBranch() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Launcher450SideSlideHoldHook.java"));
        String bridge = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SecurityCenterSidebarCommandBridge.java"));
        String contract = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SidebarCommandContract.java"));

        assertTrue(hook.contains("READY_STATE_RECENT"));
        assertTrue(hook.contains("secondStageDistanceReached(state)"));
        assertTrue(hook.contains("scheduleDwell("));
        assertTrue(hook.contains("HapticFeedbackConstants.LONG_PRESS"));
        assertTrue(hook.contains("onBackCancelled"));

        assertFalse(hook.contains("desktopAtDown"));
        assertFalse(hook.contains("isLauncherDesktop"));
        assertFalse(hook.contains("onDesktopProgress"));
        assertFalse(hook.contains("nativeRedirectActive"));
        assertFalse(contract.contains("EXTRA_DESKTOP"));
        assertFalse(bridge.contains("ensureDesktopDockContext"));
    }

    @Test
    public void unavailablePreflightFailsOpen() {
        SideSlideHoldPolicy policy = new SideSlideHoldPolicy();
        policy.onDown();
        policy.onReadyState("READY_STATE_RECENT");
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
        policy.onReadyState("READY_STATE_RECENT");
        int generation = policy.generation();
        assertTrue(policy.requestArm(generation));
        assertTrue(policy.onArmResult(true, generation));

        policy.onFinish();

        assertFalse(policy.commitRelease(generation));
        assertFalse(policy.shouldConsumeVendorCompletion());
    }
}
