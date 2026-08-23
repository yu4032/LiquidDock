package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Turning Liquid Glass off must tear down GPU/observer work instead of only changing a setting. */
public class GlassRuntimeDisableContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void runtimeGlassStateListensToRemotePreferenceChangesWithoutPolling() throws Exception {
        assertTrue(Files.exists(MAIN.resolve("GlassRuntimeState.java")));
        String state = Files.readString(MAIN.resolve("GlassRuntimeState.java"));
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));

        assertTrue(state.contains("OnSharedPreferenceChangeListener"));
        assertTrue(state.contains("registerOnSharedPreferenceChangeListener"));
        assertTrue(state.contains("ConfigSchema.Glass.ENABLED.name()"));
        assertTrue(module.contains("GlassRuntimeState.initialize"));
        assertFalse(state.contains("postDelayed(this, 1000"));
    }

    @Test public void disablingGlassTearsDownDockAndWorkspaceGpuResources() throws Exception {
        String state = Files.readString(MAIN.resolve("GlassRuntimeState.java"));
        String registry = Files.readString(MAIN.resolve("LauncherGlassSessionRegistry.java"));
        String drag = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String glassHook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        assertTrue(state.contains("LauncherGlassSessionRegistry.shutdownAll()"));
        assertTrue(state.contains("LauncherGlassDragOverlay.releaseAll()"));
        assertTrue(state.contains("Miuix307MaterialPipeline.onRuntimeGlassDisabled()"));
        assertTrue(state.contains("MiuixGlassHook.onRuntimeGlassDisabled()"));
        assertTrue(registry.contains("static synchronized void shutdownAll()"));
        assertTrue(drag.contains("static void releaseAll()"));
        assertTrue(glassHook.contains("Miuix307ZeroCopyRenderer.clear()"));
    }

    @Test public void disabledRuntimeCannotSilentlyRecreateGlassSessionsOrDockBinding() throws Exception {
        String registry = Files.readString(MAIN.resolve("LauncherGlassSessionRegistry.java"));
        String node = Files.readString(MAIN.resolve("LauncherGlassStaticNode.java"));
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        assertTrue(registry.contains("if (!GlassRuntimeState.isEnabled()) return null;"));
        assertTrue(node.contains("if (!GlassRuntimeState.isEnabled()) return null;"));
        assertTrue(pipeline.contains("if (!GlassRuntimeState.isEnabled()) return false;"));
    }

    @Test public void vendorBlurSuppressionPassesThroughWhenGlassIsDisabled() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        assertTrue(pipeline.contains("GlassRuntimeState.isEnabled()"));
        assertTrue(hook.contains("restoreVendorMaterialBody"));
        assertTrue(hook.contains("removeVendorGpuBlurSuppressor()"));
    }
}
