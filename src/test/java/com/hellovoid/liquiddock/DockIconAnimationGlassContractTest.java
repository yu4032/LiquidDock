package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Launcher 4.50 Dock ShortcutIcon uses the same FloatingIcon visual owner as Workspace icons. */
public class DockIconAnimationGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void floatingIconBridgeRoutesDockTargetsWithoutJoiningWorkspaceSession() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        String registry = Files.readString(MAIN.resolve("DockGlassItemRegistry.java"));

        assertTrue(hook.contains("LauncherGlassHierarchy.Domain.DOCK"));
        assertTrue(hook.contains("DockGlassItemRegistry.holdLaunchProxyHidden"));
        assertTrue(hook.contains("DockGlassItemRegistry.updateLaunchProxyGeometry"));
        assertTrue(hook.contains("DockGlassItemRegistry.endLaunchProxy"));
        assertTrue(hook.contains("resolveProxyCoordinateRoot"));

        assertTrue(registry.contains("LauncherGlassVisualOwnerState"));
        assertFalse(registry.contains("LauncherGlassSession"));
        assertFalse(registry.contains("LauncherGlassSceneController"));
    }

    @Test
    public void dockProxyKeepsLauncherRootCoordinateAuthorityUntilDockCapture() throws Exception {
        String registry = Files.readString(MAIN.resolve("DockGlassItemRegistry.java"));
        String item = Files.readString(MAIN.resolve("DockGlassItemNode.java"));

        // FloatingIconView2/FloatingIconLayer2 update rects are Launcher-root local. Preserve that
        // root together with the rect, then map root -> global -> Dock output at capture time.
        assertTrue(registry.contains("ProxySnapshot"));
        assertTrue(registry.contains("coordinateRoot"));
        assertTrue(registry.contains("copyLaunchProxy"));
        assertTrue(item.contains("DockGlassItemRegistry.copyLaunchProxy(view)"));
        assertTrue(item.contains("proxy.coordinateRoot.transformMatrixToGlobal"));
        assertTrue(item.contains("outputInverse.mapPoints"));
    }

    @Test
    public void dockProxyStateForcesNextDockPredrawAndParticipatesInFingerprint() throws Exception {
        String registry = Files.readString(MAIN.resolve("DockGlassItemRegistry.java"));
        String item = Files.readString(MAIN.resolve("DockGlassItemNode.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(registry.contains("postInvalidateOnAnimation"));
        assertTrue(registry.contains("visualFingerprint"));
        assertTrue(item.contains("DockGlassItemRegistry.visualFingerprint"));
        assertTrue(view.contains("ViewTreeObserver.OnPreDrawListener"));
        assertTrue(view.contains("dockCompositor.refreshUiSceneIfNeeded"));
    }

    @Test
    public void hiddenProxyOwnsDockSlotBeforeSourceVisibilityChecks() throws Exception {
        String item = Files.readString(MAIN.resolve("DockGlassItemNode.java"));
        int proxyRead = item.indexOf("DockGlassItemRegistry.copyLaunchProxy(view)");
        int visibility = item.indexOf("LauncherGlassVisibility.isVisible(view, ownershipRoot)");

        assertTrue("proxy ownership must be resolved before hidden source visibility", proxyRead >= 0);
        assertTrue("source visibility must not discard an active FloatingIcon proxy",
                visibility < 0 || proxyRead < visibility);
        assertTrue(item.contains("if (proxy.active)"));
        assertTrue(item.contains("if (proxy.rect == null) return null"));
    }
}
