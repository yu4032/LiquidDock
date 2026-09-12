package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for Security Center material ownership and legacy shader recovery. */
public class SecurityCenterPassBlurOwnershipContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void claimedVendorBlurCannotReplaceALegacyModuleProducer() throws Exception {
        String state = Files.readString(
                MAIN.resolve("SecurityCenterVendorMaterialState.java"));
        String bridge = Files.readString(
                MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String coordinator = Files.readString(
                MAIN.resolve("SecurityCenterGlassCoordinator.java"));
        String session = Files.readString(
                MAIN.resolve("SecurityCenterGlassSession.java"));

        assertTrue("claimed vendor calls must be suppressed from reaching ViewRootImpl",
                state.contains("successfulSuppressionResult(method)"));
        assertTrue("legacy claim must clear an already-active native pass-window output",
                bridge.contains("MiBlurBridge.clearPassWindowBlur(target)"));
        assertTrue("legacy changed vendor claim keeps its producer-rebind recovery",
                coordinator.contains("requestSourceRebind("));
        assertTrue("legacy session keeps producer rebind available to the coordinator",
                session.contains("requestSourceRebind("));
    }

    @Test
    public void activeSecurityCenterPathUsesFrameworkPassWindowInsteadOfFrozenOesProducer()
            throws Exception {
        String policy = Files.readString(
                MAIN.resolve("SecurityCenterMaterialModePolicy.java"));
        String transition = Files.readString(
                MAIN.resolve("SecurityCenterGlassRuntimeTransitionPolicy.java"));
        String early = Files.readString(
                MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));
        String bridge = Files.readString(
                MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));

        assertTrue("Video/Global must select the framework producer",
                transition.contains("AssistantBackend.FRAMEWORK_PASS_WINDOW"));
        assertTrue("Security Center must use framework advanced material",
                policy.contains("return LiquidBlurMode.ADVANCED_MATERIAL;"));
        assertTrue("framework material must be driven by system pass-window blur",
                policy.contains("MiBlurBridge.setPassWindowBlurEnabled(turbo, true)")
                        && policy.contains("MiBlurBridge.applyPassWindowBlur("));
        assertTrue("semantic readiness must claim the typed framework Dock",
                early.contains("SecurityCenterVendorMaterialBridge.claimFrameworkDock(turbo, dock)"));
        assertFalse("Video/Global readiness must not create the root-bound OES/Prismal session",
                early.contains("SecurityCenterGlassRuntimeState.bindAssistant(\n                    turbo, dock"));
        assertTrue("framework claim must be owned and released through the stable vendor mirror",
                bridge.contains("claimFrameworkDockInternal")
                        && bridge.contains("SecurityCenterVendorMaterialState.claimOwner(turboLayout, dockLayout)"));
    }

    @Test
    public void realSidebarBinderLifecycleRevokesStaleCustomPresentation() throws Exception {
        String hook = Files.readString(MAIN.resolve("SecurityCenterGlassHook.java"));
        String coordinator = Files.readString(
                MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertTrue("the stable AIDL binder show/hide lifecycle must be hooked",
                hook.contains("sidebarLifecycle.show()")
                        && hook.contains("sidebarLifecycle.hideImmediate()")
                        && hook.contains("sidebarLifecycle.hideAnimated()"));
        assertTrue("an immediate vendor hide must terminate on the next main-loop turn",
                coordinator.contains("immediate sidebar hide terminal")
                        && coordinator.contains("mainHandler.post(immediateTerminal)"));
        assertTrue("animated teardown must wait for the semantic terminal-cleanup authority",
                coordinator.contains("animated sidebar hide awaiting semantic terminal cleanup")
                        && hook.contains("notifyVendorPanelTerminal(chain.getArgs(), contract)"));
        assertFalse("animated teardown must not invent a fixed-delay terminal fallback",
                coordinator.contains("postDelayed("));
        assertTrue("a new show must revoke a stale previous presentation before rebinding",
                coordinator.contains("sidebar show revoked stale presentation"));
    }
}
