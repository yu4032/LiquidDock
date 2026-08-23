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
    public void floatingIconBridgeHandsDockVisualOwnershipToLauncherStaticNode() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        Path bridgePath = MAIN.resolve("DockIconLaunchProxyBridge.java");

        assertTrue(hook.contains("LauncherGlassHierarchy.Domain.DOCK"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.holdHidden"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.update"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.end"));
        assertTrue(Files.exists(bridgePath));
        String bridge = Files.readString(bridgePath);
        assertTrue(bridge.contains("LauncherGlassStaticNode.attachLaunchProxyAnchor"));
        assertTrue(bridge.contains("DockGlassItemRegistry.holdLaunchProxyHidden"));
        assertTrue(bridge.contains("DockGlassItemRegistry.updateLaunchProxyGeometry"));
        assertTrue(bridge.contains("DockGlassItemRegistry.endLaunchProxy"));
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
        assertFalse(registry.contains("LauncherGlassSession"));
        assertFalse(item.contains("LauncherGlassSession"));
    }

    @Test
    public void layer2UsesLauncherWorkspaceAsMainWindowSessionAnchor() throws Exception {
        Path bridgePath = MAIN.resolve("DockIconLaunchProxyBridge.java");
        assertTrue(Files.exists(bridgePath));
        String bridge = Files.readString(bridgePath);

        // FloatingIconView2 is itself a View in the Launcher window. FloatingIconLayer2 is not a
        // View; Launcher 4.50 stores private Launcher launcher and renders against launcher.getRootView().
        assertTrue(bridge.contains("owner instanceof View"));
        assertTrue(bridge.contains("HookUtil.getField(owner, \"launcher\")"));
        assertTrue(bridge.contains("HookUtil.invoke(launcher, \"getWorkspace\")"));
    }

    @Test
    public void proxyNodeSeparatesLauncherSessionAnchorFromDockRadiusReference() throws Exception {
        String node = Files.readString(MAIN.resolve("LauncherGlassStaticNode.java"));

        assertTrue(node.contains("proxyReferenceRef"));
        assertTrue(node.contains("attachLaunchProxyAnchor"));
        assertTrue(node.contains("LauncherGlassIconGeometry.resolve(proxyReference)"));
        assertTrue(node.contains("proxyReference.getWidth()"));
        assertTrue(node.contains("proxyReference.getHeight()"));
    }

    @Test
    public void hiddenVisibleEndTriStateMatchesWorkspaceSemantics() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        Path bridgePath = MAIN.resolve("DockIconLaunchProxyBridge.java");
        assertTrue(Files.exists(bridgePath));
        String bridge = Files.readString(bridgePath);

        assertTrue(bridge.contains("node.holdLaunchProxyHidden()"));
        assertTrue(bridge.contains("node.updateLaunchProxyGeometry"));
        assertTrue(bridge.contains("node.endLaunchProxy()"));
        assertTrue(bridge.contains("node.dispose()"));
        assertTrue(hook.contains("visibility == View.VISIBLE"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.end(host)"));
    }

    @Test
    public void dockCompositorAndProxyBridgeOwnNoNewOutputResources() throws Exception {
        String compositor = Files.readString(MAIN.resolve("DockGlassCompositor.java"));
        Path bridgePath = MAIN.resolve("DockIconLaunchProxyBridge.java");
        assertTrue(Files.exists(bridgePath));
        String bridge = Files.readString(bridgePath);

        assertFalse(compositor.contains("LauncherGlassSession"));
        assertFalse(compositor.contains("LauncherGlassSceneController"));
        assertFalse(bridge.contains("new TextureView("));
        assertFalse(bridge.contains("new SurfaceTexture("));
        assertFalse(bridge.contains("EGLSurface "));
    }
}
