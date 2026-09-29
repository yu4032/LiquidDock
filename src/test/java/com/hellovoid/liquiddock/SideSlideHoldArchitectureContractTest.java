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
        assertTrue(hook.contains("lazily resolved LauncherState.NORMAL"));
        assertTrue(hook.contains("normal = stateClass.getField(\"NORMAL\").get(null);"));
        assertFalse(hook.contains(
                "Object normal = stateClass.getField(\"NORMAL\").get(null);"));
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
        assertTrue(hook.contains("Sidebar preflight ready -> haptic then start native SC transform"));
        assertTrue(hook.contains("native confirmation haptic complete -> start SC transform"));
        assertTrue(hook.contains("ACTION_UP -> finalize Sidebar gesture"));
        assertTrue(hook.contains("Sidebar source geometry side="));
        assertTrue(hook.contains("int x = state.leftEdge ? 0 : screenWidth - width;"));
        assertTrue(hook.contains("centerY = !Float.isNaN(state.hoverAnchorY)"));
        assertTrue(hook.contains("state.hoverAnchorY"));
        assertTrue(hook.contains("centerY - (height / 2f)"));
        assertTrue(hook.contains("OS4_SOURCE_WIDTH_DP = 30f"));
        assertTrue(hook.contains("OS4_SOURCE_HEIGHT_DP = 53f"));
        assertTrue(hook.contains("OS4_SOURCE_RADIUS_DP = 8f"));
        assertFalse(hook.contains("OS4_SOURCE_TOP_OFFSET_PX"));
        assertFalse(hook.contains("getLocationOnScreen(location)"));
        assertFalse(bridge.contains("moveSidebarToGestureY"));
        assertFalse(bridge.contains("resolveOrPrepareMainSidebarWrapper"));
        assertTrue(hook.contains("shouldConsumeVendorCompletion"));
        assertFalse(hook.contains("if (consumeDesktop)"));
        assertTrue(hook.contains("HOME Workspace paging cancelled for edge side-slide"));
        assertTrue(hook.contains("finishCurrentGesture"));
        assertTrue(contract.contains("ACTION_CONFIRM_START"));
        assertTrue(hook.contains("sendOrderedBroadcast"));
        assertTrue(hook.contains("sidebarShowIssued"));
        assertTrue(contract.contains("ACTION_CONFIRM_END"));
        assertTrue(contract.contains("EXTRA_DESKTOP"));
        assertTrue(bridge.contains("vendorShowEndpointReady"));
        assertTrue(bridge.contains("logVendorBooleanDiagnostics"));
        assertTrue(bridge.contains("show.invoke"));
        assertTrue(contract.contains("EXTRA_GESTURE_Y"));
        assertTrue(bridge.contains("native confirmation haptic"));
        assertTrue(bridge.contains("resolveDockState"));
        assertTrue(bridge.contains("DockWindowType{"));
        assertTrue(bridge.contains("resolveUniqueVoidIntMethod"));
        assertFalse(bridge.contains("activate+haptic+widen"));
        assertFalse(bridge.contains("cleanup+narrow"));
        assertTrue(bridge.contains("RegionSamplingImageView"));
        assertTrue(bridge.contains("VENDOR_SHOW_ENTRY"));
        assertTrue(bridge.contains("resolveManagerFiveIntShowMethod"));
        assertTrue(bridge.contains("desktop dock context"));
        assertTrue(bridge.contains("setType.invoke(dockState, 4)"));
        assertFalse(bridge.contains("if (!vendorAvailableOrShowing())"));
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
        assertFalse(bridge.contains("\"p1\""));
    }
}
