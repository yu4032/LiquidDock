package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Architecture contract: widget background ownership has one semantic facade. */
public class LauncherWidgetBackgroundControllerContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void widgetLifecycleUsesOneBackgroundController() throws Exception {
        Path controllerPath = MAIN.resolve("LauncherWidgetBackgroundController.java");
        assertTrue(Files.exists(controllerPath));
        String controller = Files.readString(controllerPath);
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        String rootHook = Files.readString(MAIN.resolve("LauncherMamlRootLoadedHook.java"));

        assertTrue(controller.contains("static void claim(View host)"));
        assertTrue(controller.contains("static void release(View host)"));
        assertTrue(controller.contains("static void claimLoadedMamlRoot(View host, Object root)"));
        assertTrue(controller.contains("LauncherGlassVendorMaterialSuppressor.claimWidgetMaterial(host)"));
        assertTrue(controller.contains("LauncherMamlBackgroundRuleExecutor.claim(host)"));
        assertTrue(controller.contains("LauncherMamlBackgroundRuleExecutor.release(host)"));

        assertTrue(hook.contains("LauncherWidgetBackgroundController.claim(host)"));
        assertTrue(hook.contains("LauncherWidgetBackgroundController.release(host)"));
        assertFalse(hook.contains("LauncherGlassVendorMaterialSuppressor.claimWidget(host)"));
        assertFalse(hook.contains("LauncherGlassVendorMaterialSuppressor.releaseWidget(host)"));

        assertTrue(rootHook.contains("LauncherWidgetBackgroundController.claimLoadedMamlRoot"));
        assertFalse(rootHook.contains("LauncherMamlBackgroundRuleExecutor.claimLoadedRoot"));
    }

    @Test public void vendorMaterialHelperContainsNoMamlRuleKnowledge() throws Exception {
        String vendor = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));

        assertTrue(vendor.contains("claimWidgetMaterial"));
        assertTrue(vendor.contains("releaseWidgetMaterial"));
        assertTrue(vendor.contains("claimFolderMaterial"));
        assertFalse(vendor.contains("LauncherMamlBackgroundRuleExecutor"));
        assertFalse(vendor.contains("LauncherMamlBackground"));
        assertFalse(vendor.contains("WidgetBackgroundRule"));
        assertFalse(vendor.contains("findElement"));
    }
}
