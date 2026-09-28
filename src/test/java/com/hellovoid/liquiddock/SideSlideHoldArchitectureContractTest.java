package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class SideSlideHoldArchitectureContractTest {
    @Test
    public void launcherUsesVendorReadyStateInsteadOfDuplicatingGestureThresholds() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Launcher450SideSlideHoldHook.java"));
        String policy = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SideSlideHoldPolicy.java"));

        assertTrue(hook.contains("GestureBackArrowView$ReadyState"));
        assertTrue(hook.contains("setReadyFinish"));
        assertTrue(policy.contains("READY_STATE_RECENT"));

        assertFalse(hook.contains("BACK_COMPLETE_DISTANCE_PX"));
        assertFalse(hook.contains("STABILITY_SLOP_PX"));
        assertFalse(policy.contains("180f"));
        assertFalse(policy.contains("20f"));
        assertFalse(hook.contains("mAssistX1"));
        assertFalse(hook.contains("mAssistX2"));
    }

    @Test
    public void sidebarAcknowledgementPrecedesVendorCompletionSuppression() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Launcher450SideSlideHoldHook.java"));
        String bridge = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SecurityCenterSidebarCommandBridge.java"));
        String contract = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SidebarCommandContract.java"));

        assertTrue(hook.contains("sendOrderedBroadcast"));
        assertTrue(hook.contains("RESULT_ACCEPTED"));
        assertTrue(hook.contains("shouldConsumeVendorCompletion"));
        assertTrue(bridge.contains("show.invoke"));
        assertTrue(contract.contains("ISidebarOverlay"));

        assertFalse(contract.contains("ACTION_PREPARE"));
        assertFalse(hook.contains("PREPARE"));
    }

    @Test
    public void vendorBinderContractRemainsNameIndependent() throws Exception {
        String bridge = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SecurityCenterSidebarCommandBridge.java"));

        assertTrue(bridge.contains("getInterfaceDescriptor"));
        assertTrue(bridge.contains("params.length != 5"));
        assertTrue(bridge.contains("method.getReturnType() != boolean.class"));
        assertFalse(bridge.contains("\"a7\""));
        assertFalse(bridge.contains("\"D\""));
        assertFalse(bridge.contains("\"p1\""));
    }
}
