package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts proven by the working Security Center framework-material path. */
public class SecurityCenterFrameworkDockContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

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

        int bindStart = coordinator.indexOf("void bindAssistant(");
        int nextMethod = coordinator.indexOf("\n    void updateAllAppsLayout", bindStart);
        assertTrue(bindStart >= 0 && nextMethod > bindStart);
        String bindBody = coordinator.substring(bindStart, nextMethod);
        assertTrue("semantic Dock bind must claim/apply framework material eagerly",
                bindBody.contains("claimFrameworkDock("));
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

        int restoreStart = hook.indexOf("HookUtil.hook(restoreVendor");
        int suppressStart = hook.indexOf("HookUtil.hook(suppressFinal", restoreStart);
        assertTrue(restoreStart >= 0 && suppressStart > restoreStart);
        String restoreHook = hook.substring(restoreStart, suppressStart);
        assertFalse("normal panel close must not clear retained framework Dock material",
                restoreHook.contains("releaseAdvancedMaterial()"));

        int releaseStart = hook.indexOf("HookUtil.hook(releaseAll");
        assertTrue(releaseStart >= 0);
        String releaseHook = hook.substring(releaseStart);
        assertTrue("runtime/full release must clear module material and restore vendor state",
                releaseHook.contains("releaseAdvancedMaterial()"));
    }
}
