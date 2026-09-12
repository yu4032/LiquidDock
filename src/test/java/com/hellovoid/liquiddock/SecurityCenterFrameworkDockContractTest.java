package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static architecture contracts proven by the working Security Center framework-material path. */
public class SecurityCenterFrameworkDockContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void securityCenterAlwaysUsesFrameworkBackend() throws Exception {
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));

        assertFalse("Security Center must not inherit Launcher's persisted shader selection",
                policy.contains("ConfigReader config = ConfigReader.load()"));
        assertTrue("Security Center background backend is the verified framework material",
                policy.contains("return LiquidBlurMode.ADVANCED_MATERIAL;"));
    }

    @Test
    public void frameworkDockReadinessDoesNotDependOnShaderSourceAuthority() throws Exception {
        String runtime = Files.readString(MAIN.resolve("SecurityCenterGlassRuntimeState.java"));
        String prepare = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));

        assertTrue("framework material needs a config-only enable gate",
                runtime.contains("static boolean isMaterialEnabled()"));
        assertTrue("deferred Dock preparation must use the config-only material gate",
                prepare.contains("SecurityCenterGlassRuntimeState.isMaterialEnabled()"));
    }

    @Test
    public void semanticDockBindPreparesFrameworkMaterialBeforeAnyFreshFrame() throws Exception {
        String coordinator = Files.readString(MAIN.resolve("SecurityCenterGlassCoordinator.java"));
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));

        assertTrue("semantic Dock bind must expose eager framework material preparation",
                coordinator.contains("claimFrameworkDock("));
        assertTrue("framework Dock claim must exist independently of shader presentation",
                bridge.contains("claimFrameworkDock("));
    }

    @Test
    public void frameworkOwnershipIsDockOnlyAndAllAppsStaysVendorOwned() throws Exception {
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));

        assertFalse("All Apps must never be an advanced-material carrier",
                policy.contains("advancedApps"));
        assertFalse("All Apps must never receive module pass-window blur",
                policy.contains("applyPassWindowBlur(\n                    apps"));
        assertFalse("Turbo root is pass-window host only, not a cleared material carrier",
                bridge.contains("clearVendorTarget(turboView)"));
        assertFalse("All Apps must keep its vendor material",
                bridge.contains("clearVendorTarget(allAppsLayout)"));
    }

    @Test
    public void normalPanelCloseRetainsFrameworkDockButFullReleaseRestoresVendor() throws Exception {
        String hook = Files.readString(MAIN.resolve("SecurityCenterAdvancedMaterialHook.java"));

        assertTrue("normal panel close needs an explicit retained-material policy",
                hook.contains("retainFrameworkDockOnPanelClose"));
        assertFalse("restore hook must not eagerly clear retained Dock material",
                hook.contains("HookUtil.hook(restoreVendor, chain -> {\n"
                        + "                SecurityCenterMaterialModePolicy.releaseAdvancedMaterial();"));
        assertTrue("runtime/full release must still expose full advanced-material cleanup",
                hook.contains("SecurityCenterMaterialModePolicy.releaseAdvancedMaterial()"));
    }
}
