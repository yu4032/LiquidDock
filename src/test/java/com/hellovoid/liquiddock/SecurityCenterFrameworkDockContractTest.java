package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static architecture contracts for Security Center shader ownership plus dormant framework fallback. */
public class SecurityCenterFrameworkDockContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void securityCenterUsesPrismalShaderBackendWhileFrameworkCapabilityStaysDormant()
            throws Exception {
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));

        assertFalse("Security Center must not inherit Launcher's persisted shader selection",
                policy.contains("ConfigReader config = ConfigReader.load()"));
        assertTrue("Video/Global must reactivate the Prismal shader material path",
                policy.contains("return LiquidBlurMode.SHADER;"));
        assertTrue("active Security Center Prismal rendering must include shader blur",
                policy.contains("static boolean useShaderBlur() {\n        return true;"));
        assertTrue("verified framework pass-window material remains available as a dormant capability",
                policy.contains("MiBlurBridge.setPassWindowBlurEnabled(turbo, true)")
                        && policy.contains("MiBlurBridge.applyPassWindowBlur("));
    }

    @Test
    public void frameworkReadinessDoesNotRequireShaderSourceAuthority() throws Exception {
        String runtime = Files.readString(MAIN.resolve("SecurityCenterGlassRuntimeState.java"));
        String prepare = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));

        assertTrue("material feature keeps a config-only gate",
                runtime.contains("static boolean isMaterialEnabled()"));
        assertTrue("shader readiness retains its source-authority gate",
                runtime.contains("return isMaterialEnabled() && sourceAuthorityAvailable;"));
        assertTrue("dormant framework Dock preparation keeps the config-only material gate",
                prepare.contains("SecurityCenterGlassRuntimeState.isMaterialEnabled()"));
    }

    @Test
    public void semanticDockReadinessKeepsDormantFrameworkClaimAvailable() throws Exception {
        String prepare = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));

        assertTrue("semantic Dock readiness keeps the framework carrier capability available",
                prepare.contains("SecurityCenterVendorMaterialBridge.claimFrameworkDock(turbo, dock)"));
        assertTrue("the bridge must expose the typed framework claim",
                bridge.contains("static boolean claimFrameworkDock("));
    }

    @Test
    public void legacyCustomPreparingStillLatchesVendorFallbackBeforeDestructiveHandoff()
            throws Exception {
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String coordinator = Files.readString(
                MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertTrue("CUSTOM_PREPARING must keep its non-destructive fallback latch",
                bridge.contains("void protectVendorFallback("));
        assertTrue("the shader coordinator must fail closed before ACK",
                coordinator.contains("vendorMaterialBridge.protectVendorFallback("));
        assertTrue("destructive clearing remains isolated behind claimCustom",
                bridge.contains("void claimCustom(")
                        && bridge.contains("clearVendorTarget(dockLayout)"));
    }

    @Test
    public void frameworkOwnershipCoversDockOnlyAndNeverClearsTurboRoot() throws Exception {
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));

        assertFalse("framework policy must not add an All Apps material carrier",
                policy.contains("advancedApps"));
        assertFalse("All Apps must never receive module pass-window blur",
                policy.contains("applyPassWindowBlur(\n                    apps"));
        assertFalse("Turbo root is a pass-window host, not a cleared material carrier",
                bridge.contains("clearVendorTarget(turboView)"));
        assertTrue("framework claim must own only the semantic Dock carrier",
                bridge.contains("SecurityCenterVendorMaterialState.claimOwner(turboLayout, dockLayout)"));
        assertTrue("Dock vendor material must be cleared before framework material is applied",
                bridge.contains("clearVendorTarget(dockLayout)"));
        assertTrue("framework policy must configure only Turbo + Dock",
                policy.contains("configureAdvancedMaterial(View turbo, View dock)"));
    }

    @Test
    public void normalPanelCloseRetainsFrameworkDockButFullReleaseRestoresVendor() throws Exception {
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String coordinator = Files.readString(
                MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertTrue("normal close may retain the verified framework Dock claim",
                bridge.contains("SecurityCenterMaterialModePolicy.retainFrameworkDockOnPanelClose()"));
        assertTrue("runtime/Game full release must release all material claims",
                coordinator.contains("SecurityCenterVendorMaterialBridge.releaseClaim()"));
        assertTrue("full release must replay the latest vendor state",
                bridge.contains("SecurityCenterVendorMaterialState.restoreOwner(owner)"));
        assertTrue("full framework release must clear module pass-window material first",
                bridge.contains("SecurityCenterMaterialModePolicy.releaseAdvancedMaterial()"));
    }

    @Test
    public void securityCenterOwnImplementationUsesTypedCallsInsteadOfSelfReflection()
            throws Exception {
        String moduleMain = Files.readString(MAIN.resolve("ModuleMain.java"));
        String earlyPrepare = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String policy = Files.readString(MAIN.resolve("SecurityCenterGlassRuntimeTransitionPolicy.java"));

        assertFalse(moduleMain.contains("SecurityCenterAdvancedMaterialHook"));
        assertFalse(earlyPrepare.contains("SecurityCenterAdvancedMaterialHook"));
        assertFalse(Files.exists(MAIN.resolve("SecurityCenterAdvancedMaterialHook.java")));
        assertTrue(moduleMain.contains("SecurityCenterVendorMaterialState.install()"));
        assertTrue(policy.contains("CUSTOM_SHADER"));
        assertTrue(earlyPrepare.contains("transition.backend"));
        assertTrue(bridge.contains("SecurityCenterVendorMaterialState.claimOwner("));
    }

    @Test
    public void customShaderReclaimsVideoAndGlobalAtTypedPrepareBoundary() throws Exception {
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));
        String prepare = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));

        assertTrue(policy.contains("return LiquidBlurMode.SHADER;"));
        assertTrue(policy.contains("static boolean useShaderBlur() {\n        return true;"));
        assertTrue("active shader readiness must require the source-authority gate",
                prepare.contains("SecurityCenterGlassRuntimeState.isEnabled()"));
        assertTrue("Video/Global preparation must bind the root-bound OES/Prismal session",
                prepare.contains("SecurityCenterGlassRuntimeState.bindAssistant(\n                        turbo, dock, boxMaterial, pending.type)"));
        assertTrue("custom ownership must still latch vendor fallback before destructive handoff",
                bridge.contains("claimCustomInternal")
                        && bridge.contains("protectVendorFallbackInternal("));
    }
}
