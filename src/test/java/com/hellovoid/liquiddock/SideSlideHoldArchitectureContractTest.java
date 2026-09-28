package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class SideSlideHoldArchitectureContractTest {
    @Test
    public void appGestureUsesVendorReadyStateAndHomeFallbackIsExplicitlyScoped() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Launcher450SideSlideHoldHook.java"));
        String policy = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SideSlideHoldPolicy.java"));

        assertTrue(hook.contains("GestureBackArrowView$ReadyState"));
        assertTrue(hook.contains("setReadyFinish"));
        assertTrue(policy.contains("READY_STATE_RECENT"));
        assertTrue(hook.contains("LauncherState.NORMAL"));
        assertTrue(hook.contains("launcherIsInStateMethod"));
        assertTrue(hook.contains("launcherStateNormal"));
        assertTrue(hook.contains("HOME_VISUAL_SATURATION_PX"));
        assertTrue(hook.contains("HOME_HOVER_SLOP_DP"));
        assertTrue(hook.contains("HOME hover confirmed"));
        assertTrue(hook.contains("abs(dx) / 180f"));

        assertFalse(hook.contains("BACK_COMPLETE_DISTANCE_PX"));
        assertFalse(hook.contains("STABILITY_SLOP_PX"));
        assertFalse(hook.contains("mAssistX1"));
        assertFalse(hook.contains("mAssistX2"));
    }

    @Test
    public void os4ConfirmationOrderDoesNotArmOnPreflight() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Launcher450SideSlideHoldHook.java"));
        String bridge = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SecurityCenterSidebarCommandBridge.java"));
        String contract = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/SidebarCommandContract.java"));

        assertTrue(contract.contains("ACTION_PREPARE"));
        assertTrue(contract.contains("RESULT_READY"));
        assertTrue(hook.contains("Sidebar preflight ready -> haptic -> vendor show"));
        assertTrue(hook.contains("RESULT_ACCEPTED"));
        assertTrue(hook.contains("shouldConsumeVendorCompletion"));
        assertTrue(bridge.contains("vendorAvailableOrShowing"));
        assertTrue(bridge.contains("show.invoke"));
        assertTrue(bridge.contains("DockWindowManagerService.onCreate"));
        assertTrue(bridge.contains("ensureInstalled((Context) service)"));
        assertFalse(bridge.contains("tryInvokeActivityThreadCurrentApplication"));
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
