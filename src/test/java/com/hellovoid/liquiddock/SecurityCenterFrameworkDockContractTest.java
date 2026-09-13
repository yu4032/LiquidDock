package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static architecture contracts for the verified Security Center custom-glass path. */
public class SecurityCenterFrameworkDockContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void securityCenterAlwaysUsesCustomShaderBackend() throws Exception {
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));

        assertFalse("Security Center must not inherit Launcher's persisted shader selection",
                policy.contains("ConfigReader config = ConfigReader.load()"));
        assertTrue("Security Center must use LiquidDock's shader backend",
                policy.contains("return LiquidBlurMode.SHADER;"));
        assertTrue("Security Center sessions must use shader blur",
                policy.contains("static boolean useShaderBlur() {\n        return true;"));
    }

    @Test
    public void customGlassReadinessRequiresShaderSourceAuthority() throws Exception {
        String runtime = Files.readString(MAIN.resolve("SecurityCenterGlassRuntimeState.java"));
        String prepare = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));

        assertTrue("custom glass keeps a config-only material gate",
                runtime.contains("static boolean isMaterialEnabled()"));
        assertTrue("custom glass readiness must include live shader source authority",
                runtime.contains("return isMaterialEnabled() && sourceAuthorityAvailable;"));
        assertTrue("deferred Dock preparation must use the custom session gate",
                prepare.contains("SecurityCenterGlassRuntimeState.isEnabled()"));
    }

    @Test
    public void semanticDockReadinessPreparesCustomSessionBeforeVendorHandoff() throws Exception {
        String prepare = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));

        assertTrue("semantic Dock readiness must bind the custom session",
                prepare.contains("SecurityCenterGlassRuntimeState.bindAssistant("));
        assertTrue("vendor material must be claimed only at the custom handoff",
                bridge.contains("void claimCustom("));
        assertFalse("the old framework-material claim must not remain",
                prepare.contains("claimFrameworkDock(") || bridge.contains("claimFrameworkDock("));
    }

    @Test
    public void customPreparingLatchesVendorFallbackBeforeDestructiveHandoff() throws Exception {
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String coordinator = Files.readString(
                MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertTrue("CUSTOM_PREPARING must expose a non-destructive vendor fallback latch",
                bridge.contains("void protectVendorFallback("));
        assertTrue("the coordinator must protect the current vendor material before waiting for ACK",
                coordinator.contains("bridge.protectVendorFallback("));
        assertTrue("destructive vendor clearing must remain a separate presentation handoff",
                bridge.contains("void claimCustom(")
                        && bridge.contains("clearVendorTarget(dockLayout)"));
    }

    @Test
    public void frameworkOwnershipCoversDockToolboxAndAllAppsButNeverTheTurboRoot() throws Exception {
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));

        assertFalse("policy must not revive the removed advanced-material carriers",
                policy.contains("advancedApps"));
        assertFalse("All Apps must never receive module pass-window blur",
                policy.contains("applyPassWindowBlur(\n                    apps"));
        assertFalse("Turbo root is a source host, not a cleared material carrier",
                bridge.contains("clearVendorTarget(turboView)"));
        assertTrue("Dock vendor material must be cleared at the custom handoff",
                bridge.contains("clearVendorTarget(dockLayout)"));
        assertTrue("the upper toolbox carrier must be cleared at the custom handoff",
                bridge.contains("clearVendorTarget(boxMaterialView)"));
        assertTrue("All Apps vendor material must be cleared at the custom handoff",
                bridge.contains("clearVendorTarget(allAppsLayout)"));
        assertTrue("the claim must register exactly the three cleared carriers",
                bridge.contains("dockLayout, boxMaterialView, allAppsLayout"));
    }

    @Test
    public void panelCloseRestoresVendorAfterCustomGlassRelease() throws Exception {
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String coordinator = Files.readString(
                MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertFalse("custom glass must not retain a framework-material claim after close",
                bridge.contains("retainFrameworkDockOnPanelClose"));
        assertTrue("runtime/full release must release the custom vendor claim",
                coordinator.contains("SecurityCenterVendorMaterialBridge.releaseClaim()"));
        assertTrue("custom release must replay the latest vendor state",
                bridge.contains("SecurityCenterVendorMaterialState.restoreOwner(owner)"));
    }

    @Test
    public void securityCenterOwnImplementationUsesTypedCallsInsteadOfSelfReflection()
            throws Exception {
        String moduleMain = Files.readString(MAIN.resolve("ModuleMain.java"));
        String earlyPrepare = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));
        String coordinator = Files.readString(MAIN.resolve("SecurityCenterGlassCoordinator.java"));
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));
        String sink = Files.readString(MAIN.resolve("SecurityCenterGlassSinkView.java"));
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));

        assertFalse(moduleMain.contains("SecurityCenterAdvancedMaterialHook"));
        assertFalse(earlyPrepare.contains("SecurityCenterAdvancedMaterialHook"));
        assertFalse(Files.exists(MAIN.resolve("SecurityCenterAdvancedMaterialHook.java")));
        assertTrue(moduleMain.contains("SecurityCenterVendorMaterialState.install()"));
        assertTrue(coordinator.contains("SecurityCenterMaterialModePolicy.prepareBind(turboLayout)"));
        assertTrue(session.contains("Miuix307PrismalAdapter.toPortable("));
        assertTrue(session.contains("SecurityCenterMaterialModePolicy.useShaderBlur()"));
        assertTrue(sink.contains("setAuthorizedVisible(boolean visible)"));
        assertTrue(bridge.contains("SecurityCenterVendorMaterialState.claimOwner("));
    }

    @Test
    public void customGlassReplacesAdvancedDockMaterialAtTheTypedClaimBoundary()
            throws Exception {
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));
        String prepare = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));

        assertTrue(policy.contains("return LiquidBlurMode.SHADER;"));
        assertTrue(policy.contains("static boolean useShaderBlur() {\n        return true;"));
        assertTrue(prepare.contains("SecurityCenterGlassRuntimeState.isEnabled()"));
        assertFalse(prepare.contains("claimFrameworkDock(turbo, dock)"));
        assertTrue(prepare.contains("SecurityCenterGlassRuntimeState.bindAssistant("));
        assertTrue(session.contains("SecurityCenterMaterialModePolicy.useShaderBlur()"));

        assertTrue(bridge.contains("void claimCustom("));
        assertTrue(bridge.contains("void restoreVendor("));
        assertFalse(bridge.contains("claimFrameworkDockInternal"));
        assertFalse(bridge.contains("configureAdvancedMaterial"));
        assertTrue(bridge.contains("SecurityCenterVendorMaterialState.claimOwner("));
        assertTrue(bridge.contains("clearVendorTarget(dockLayout)"));
    }
}
