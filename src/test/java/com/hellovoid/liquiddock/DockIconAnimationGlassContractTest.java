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
    public void floatingIconBridgePrefersFrozenSurfaceControlLayer() throws Exception {
        String hook = Files.readString(MAIN.resolve("DockIconLaunchProxyHook.java"));
        String bridge = Files.readString(MAIN.resolve("DockIconLaunchProxyBridge.java"));

        assertTrue(hook.contains("LauncherGlassHierarchy.Domain.DOCK"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.holdHidden"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.update"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.end"));
        assertTrue(bridge.contains("DockIconFrozenGlassLayer"));
        assertTrue(bridge.contains("DockGlassItemRegistry.holdLaunchProxyHidden"));
        assertTrue(bridge.contains("DockGlassItemRegistry.endLaunchProxy"));
    }

    @Test
    public void dockStaticItemYieldsForEntireVendorProxyLifetime() throws Exception {
        String registry = Files.readString(MAIN.resolve("DockGlassItemRegistry.java"));
        String item = Files.readString(MAIN.resolve("DockGlassItemNode.java"));

        assertTrue(registry.contains("LauncherGlassVisualOwnerState"));
        assertTrue(registry.contains("isLaunchProxyActive"));
        assertTrue(item.contains("DockGlassItemRegistry.isLaunchProxyActive(view)"));
        assertTrue(item.contains("return null"));
        assertFalse(registry.contains("LauncherGlassSession"));
        assertFalse(item.contains("LauncherGlassSession"));
    }

    @Test
    public void frozenFrameIsCapturedBeforeDockItemYields() throws Exception {
        String bridge = Files.readString(MAIN.resolve("DockIconLaunchProxyBridge.java"));
        int ensure = bridge.indexOf("ensureBinding(owner, target, glassConfig)");
        int hide = bridge.indexOf("DockGlassItemRegistry.holdLaunchProxyHidden(target)");
        assertTrue(ensure >= 0 && hide > ensure);
    }

    @Test
    public void frozenProxyUsesLauncherRootBufferLayerWithoutScreenCapture() throws Exception {
        Path frozenPath = MAIN.resolve("DockIconFrozenGlassLayer.java");
        assertTrue(Files.exists(frozenPath));
        String frozen = Files.readString(frozenPath);

        assertTrue(frozen.contains("SurfaceControlUtils"));
        assertTrue(frozen.contains("getBufferLayer"));
        assertTrue(frozen.contains("SurfaceCompat"));
        assertFalse(frozen.contains("PixelCopy"));
        assertFalse(frozen.contains("ImageReader"));
        assertFalse(frozen.contains("MediaProjection"));
        assertFalse(frozen.contains("glReadPixels"));
    }

    @Test
    public void dockRendererFreezesExistingBackdropExactlyOnceWithoutChangingCoreTextureView()
            throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String texture = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String compositor = Files.readString(MAIN.resolve("DockGlassCompositor.java"));
        Path helperPath = MAIN.resolve("DockIconFrozenGlassRenderer.java");
        assertTrue(Files.exists(helperPath));
        String helper = Files.readString(helperPath);

        assertTrue(renderer.contains("captureFrozenIconSpec"));
        assertTrue(renderer.contains("renderFrozenIcon"));
        assertTrue(renderer.contains("DockIconFrozenGlassRenderer.capture"));
        assertTrue(renderer.contains("DockIconFrozenGlassRenderer.render"));
        assertTrue(helper.contains("FrozenIconSpec"));
        assertTrue(helper.contains("renderFrozenIconOnce"));
        assertTrue(helper.contains("rawTexture"));
        assertTrue(helper.contains("PrismalRenderer"));
        assertTrue(compositor.contains("captureUiItem"));
        assertFalse(texture.contains("FrozenIconSpec"));
        assertFalse(texture.contains("frozenProxyRenderer"));

        int start = helper.indexOf("renderFrozenIconOnce");
        int end = helper.indexOf("private static void postMain", start);
        String body = end > start ? helper.substring(start, end) : helper.substring(start);
        assertFalse(body.contains("requestSingleUpdate"));
        assertFalse(body.contains("rebindProducer"));
        assertFalse(body.contains("setProducerUpdatesEnabled"));
        assertFalse(body.contains("PixelCopy"));
        assertFalse(body.contains("glReadPixels"));
    }

    @Test
    public void visibleAnimationHotPathOnlyMutatesSurfaceTransaction() throws Exception {
        String hook = Files.readString(MAIN.resolve("DockIconLaunchProxyHook.java"));
        String frozen = Files.readString(MAIN.resolve("DockIconFrozenGlassLayer.java"));

        assertTrue(hook.contains("Object result = chain.proceed(args)"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.update(\n"));
        assertTrue(hook.contains("result, glassConfig"));
        assertTrue(frozen.contains("setMatrix"));
        assertTrue(frozen.contains("setRelativeLayer"));
        assertTrue(frozen.contains("setAlpha"));
        assertTrue(frozen.contains("show"));

        int start = frozen.indexOf("void update(");
        int end = frozen.indexOf("void holdHidden", start);
        String update = end > start ? frozen.substring(start, end) : frozen.substring(start);
        assertFalse(update.contains("renderFrozenIcon"));
        assertFalse(update.contains("requestStaticRedraw"));
        assertFalse(update.contains("eglSwapBuffers"));
        assertFalse(update.contains("PrismalRenderer"));
    }

    @Test
    public void layer2CanMergeGlassIntoVendorTransactionAndView2CanSelfApply() throws Exception {
        String frozen = Files.readString(MAIN.resolve("DockIconFrozenGlassLayer.java"));
        assertTrue(frozen.contains("getTransaction"));
        assertTrue(frozen.contains("applyStandalone"));
        assertTrue(frozen.contains("mFloatingIconSurfaceControl"));
    }

    @Test
    public void hiddenVisibleEndTriStateStillRestoresDockOwnership() throws Exception {
        String hook = Files.readString(MAIN.resolve("DockIconLaunchProxyHook.java"));
        String bridge = Files.readString(MAIN.resolve("DockIconLaunchProxyBridge.java"));
        String runtime = Files.readString(MAIN.resolve("GlassRuntimeState.java"));

        assertTrue(bridge.contains("layer.holdHidden()"));
        assertTrue(bridge.contains("layer.update("));
        assertTrue(bridge.contains("layer.release()"));
        assertTrue(hook.contains("visibility == View.VISIBLE"));
        assertTrue(hook.contains("DockIconLaunchProxyBridge.end(host)"));
        assertTrue(runtime.contains("DockIconLaunchProxyBridge.clear()"));
    }
}
