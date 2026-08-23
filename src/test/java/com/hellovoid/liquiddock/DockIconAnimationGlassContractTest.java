package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Launcher 4.50 hands Dock ShortcutIcon visuals to a Launcher-root FloatingIcon proxy. */
public class DockIconAnimationGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void floatingIconBridgeHandsDockVisualOwnershipToLauncherRoot() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));

        assertTrue(hook.contains("LauncherGlassHierarchy.Domain.DOCK"));
        assertTrue(hook.contains("DockGlassItemRegistry.holdLaunchProxyHidden"));
        assertTrue(hook.contains("DockGlassItemRegistry.updateLaunchProxyGeometry"));
        assertTrue(hook.contains("DockGlassItemRegistry.endLaunchProxy"));
        assertTrue(hook.contains("LauncherGlassSceneController.updateDockIconProxy"));
        assertTrue(hook.contains("LauncherGlassSceneController.holdDockIconProxyHidden"));
        assertTrue(hook.contains("LauncherGlassSceneController.endDockIconProxyForAll"));
        assertTrue(hook.contains("resolveProxyCoordinateRoot"));
    }

    @Test
    public void dockStaticItemYieldsForEntireVendorProxyLifetime() throws Exception {
        String registry = Files.readString(MAIN.resolve("DockGlassItemRegistry.java"));
        String item = Files.readString(MAIN.resolve("DockGlassItemNode.java"));

        assertTrue(registry.contains("LauncherGlassVisualOwnerState"));
        assertTrue(registry.contains("isLaunchProxyActive"));
        assertTrue(registry.contains("postInvalidateOnAnimation"));
        assertTrue(item.contains("DockGlassItemRegistry.isLaunchProxyActive(view)"));
        assertTrue(item.contains("return null"));

        // Static Dock rendering remains its own output domain; only ownership state crosses over.
        assertFalse(registry.contains("LauncherGlassSession"));
        assertFalse(registry.contains("LauncherGlassSceneController"));
        assertFalse(item.contains("LauncherGlassSession"));
        assertFalse(item.contains("LauncherGlassSceneController"));
    }

    @Test
    public void launcherRootProxyUsesVendorRectDirectlyInsteadOfDockWindowMapping() throws Exception {
        String controller = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        // FloatingIconView2.iconRect and FloatingIconLayer2.rotationIconRect are already local to
        // Launcher.getRootView(). The full-screen Launcher static layer is therefore the final
        // consumer; do not map the proxy back through the bottom Dock window.
        assertTrue(controller.contains("updateDockIconProxy"));
        assertTrue(controller.contains("LauncherGlassGeometry.resolve"));
        assertTrue(controller.contains("glassConfig.iconStyle"));
        assertTrue(session.contains("externalStaticNodes"));
        assertTrue(session.contains("updateExternalStaticGeometry"));
        assertTrue(session.contains("removeExternalStaticGeometry"));
        assertTrue(session.contains("renderExternalStaticGeometry"));
    }

    @Test
    public void hiddenVisibleEndTriStateMatchesWorkspaceSemantics() throws Exception {
        String controller = Files.readString(MAIN.resolve("LauncherGlassSceneController.java"));
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));

        // Hidden proxy owns the slot but draws no glass. Visible proxy publishes final-consumer
        // geometry. Returning source visibility ends the Launcher-root proxy and restores Dock.
        assertTrue(controller.contains("holdDockIconProxyHidden"));
        assertTrue(controller.contains("removeExternalStaticGeometry"));
        assertTrue(controller.contains("updateDockIconProxy"));
        assertTrue(controller.contains("endDockIconProxyForAll"));
        assertTrue(hook.contains("visibility == View.VISIBLE"));
        assertTrue(hook.contains("DockGlassItemRegistry.endLaunchProxy(host)"));
    }

    @Test
    public void dockOwnerHandoffRedrawsWithoutWaitingForNewProducerFrame() throws Exception {
        String compositor = Files.readString(MAIN.resolve("DockGlassCompositor.java"));
        String dockView = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(compositor.contains("boolean refreshUiSceneIfNeeded"));
        assertTrue(dockView.contains("boolean dockSceneChanged"));
        assertTrue(dockView.contains("if (dockSceneChanged && hasConsumedFrame)"));
        assertTrue(dockView.contains("drawLatestFrame(false)"));
    }

    @Test
    public void dockCompositorStillOwnsNoWorkspaceSessionResources() throws Exception {
        String compositor = Files.readString(MAIN.resolve("DockGlassCompositor.java"));
        String dockView = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertFalse(compositor.contains("LauncherGlassSession"));
        assertFalse(compositor.contains("LauncherGlassSceneController"));
        assertFalse(dockView.contains("LauncherGlassSession"));
        assertFalse(dockView.contains("LauncherGlassSceneController"));
    }
}
