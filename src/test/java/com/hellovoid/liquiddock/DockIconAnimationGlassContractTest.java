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
    public void dockProxyHookIsInstalledWithoutChangingWorkspaceProxyHook() throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String workspaceHook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        Path hookPath = MAIN.resolve("DockIconLaunchProxyHook.java");

        assertTrue(module.contains("DockIconLaunchProxyHook.install(classLoader, runtimeConfig)"));
        assertTrue(Files.exists(hookPath));
        assertFalse(workspaceHook.contains("DockIconLaunchProxyBridge"));
    }

    @Test
    public void floatingIconBridgeHandsDockVisualOwnershipToLauncherStaticNode() throws Exception {
        Path hookPath = MAIN.resolve("DockIconLaunchProxyHook.java");
        Path bridgePath = MAIN.resolve("DockIconLaunchProxyBridge.java");
        assertTrue(Files.exists(hookPath));
        assertTrue(Files.exists(bridgePath));
        String hook = Files.readString(hookPath);
        String bridge = Files.readString(bridgePath);

        assertTrue(hook.contains("LauncherGlassHierarchy.Domain.DOCK"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.holdHidden"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.update"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.end"));
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
        String bridge = Files.readString(MAIN.resolve("DockIconLaunchProxyBridge.java"));

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
        assertTrue(node.contains("node.visualOwnerState.holdLaunchProxyHidden()"));
    }

    @Test
    public void hiddenVisibleEndTriStateMatchesWorkspaceSemantics() throws Exception {
        String hook = Files.readString(MAIN.resolve("DockIconLaunchProxyHook.java"));
        String bridge = Files.readString(MAIN.resolve("DockIconLaunchProxyBridge.java"));
        String runtime = Files.readString(MAIN.resolve("GlassRuntimeState.java"));

        assertTrue(bridge.contains("node.holdLaunchProxyHidden()"));
        assertTrue(bridge.contains("node.updateLaunchProxyGeometry"));
        assertTrue(bridge.contains("node.endLaunchProxy()"));
        assertTrue(bridge.contains("node.dispose()"));
        assertTrue(hook.contains("visibility == View.VISIBLE"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.end(host)"));
        assertTrue(runtime.contains("DockIconLaunchProxyBridge.clear()"));
    }

    @Test
    public void dockCompositorAndProxyBridgeOwnNoNewOutputResources() throws Exception {
        String compositor = Files.readString(MAIN.resolve("DockGlassCompositor.java"));
        String bridge = Files.readString(MAIN.resolve("DockIconLaunchProxyBridge.java"));

        assertFalse(compositor.contains("LauncherGlassSession"));
        assertFalse(compositor.contains("LauncherGlassSceneController"));
        assertFalse(bridge.contains("new TextureView("));
        assertFalse(bridge.contains("new SurfaceTexture("));
        assertFalse(bridge.contains("EGLSurface "));
    }
}
