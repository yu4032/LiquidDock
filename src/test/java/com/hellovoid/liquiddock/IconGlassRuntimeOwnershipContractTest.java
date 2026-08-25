package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for icon glass hot disable/re-enable in one Launcher process. */
public class IconGlassRuntimeOwnershipContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void iconHooksStayInstalledButMutationsUseLiveState() throws Exception {
        String staticHook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        String dragHook = Files.readString(MAIN.resolve("MiuixLauncherDragOverlayHook.java"));
        String animationHook = Files.readString(MAIN.resolve("DockIconAnimationGlassHook.java"));
        String registry = Files.readString(MAIN.resolve("DockGlassItemRegistry.java"));
        String node = Files.readString(MAIN.resolve("LauncherGlassStaticNode.java"));

        assertFalse(staticHook.contains("if (glassConfig.iconEnabled) {"));
        assertFalse(dragHook.contains("if (!anyStaticGlass) return false;"));
        assertFalse(animationHook.contains("|| !runtimeConfig.glass.iconStyle.enabled) return false;"));
        assertTrue(staticHook.contains("GlassRuntimeState.isIconEnabled()"));
        assertTrue(dragHook.contains("GlassRuntimeState.isIconEnabled()"));
        assertTrue(animationHook.contains("GlassRuntimeState.isIconEnabled()"));
        assertTrue(registry.contains("GlassRuntimeState.isIconEnabled()"));
        assertTrue(node.contains("GlassRuntimeState.isIconEnabled()"));
    }

    @Test
    public void disablingIconGlassReleasesStaticDockAndDragOwnership() throws Exception {
        String state = Files.readString(MAIN.resolve("GlassRuntimeState.java"));
        String staticHook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        String dragHook = Files.readString(MAIN.resolve("MiuixLauncherDragOverlayHook.java"));

        assertTrue(state.contains("onRuntimeIconGlassDisabled()"));
        assertTrue(staticHook.contains("static void onRuntimeIconGlassDisabled()"));
        assertTrue(staticHook.contains("DockGlassItemRegistry.unregister(host)"));
        assertTrue(staticHook.contains("node.kind() == LauncherGlassDragState.Kind.ICON"));
        assertTrue(dragHook.contains("static void onRuntimeIconGlassDisabled()"));
        assertTrue(state.contains("MiuixLauncherDragOverlayHook.onRuntimeIconGlassDisabled()"));
    }
}
