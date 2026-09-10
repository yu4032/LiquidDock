package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static contract for the decompiled 40011320 Global Dock / All Apps lifecycle. */
public class SecurityCenterHookLifecycleContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void globalDockBindsAfterRealConfigureThenDockReadyPath() throws Exception {
        String spec = read("src/main/java/com/hellovoid/liquiddock/SecurityCenterHookSpec.java");
        String hook = read("src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassHook.java");

        // 40011320 visible path: V(..., ja.a, ...) records type, then c0() creates the dock.
        assertTrue(spec.contains("\"V\""));
        assertTrue(spec.contains("\"c0\""));
        assertTrue(hook.contains("spec.configureDockMethod()"));
        assertTrue(hook.contains("spec.dockReadyMethod()"));
        assertFalse(hook.contains("HookUtil.hook(prepare"));
    }

    @Test
    public void allAppsIsLateBoundInsteadOfRequiredForInitialDockSession() throws Exception {
        String coordinator = read(
                "src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java");
        String hook = read("src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassHook.java");

        assertTrue(coordinator.contains(
                "void bindGlobalDock(View turboLayout, View dockLayout)"));
        assertTrue(coordinator.contains(
                "void updateAllAppsLayout(View turboLayout, View appsLayout)"));
        assertTrue(hook.contains("live.updateAllAppsLayout(turbo, (View) apps)"));
    }
}
