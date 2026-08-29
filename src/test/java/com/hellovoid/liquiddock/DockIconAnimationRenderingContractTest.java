package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class DockIconAnimationRenderingContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void animationProgressDoesNotMutateDockMembershipRevision() throws Exception {
        String registry = Files.readString(MAIN.resolve("DockGlassItemRegistry.java"));
        String animationMethods = slice(registry,
                "static synchronized void observeLaunchAnimationFrame",
                "static synchronized float animationOpacity");

        assertFalse(animationMethods.contains("revision++"));
        assertTrue(animationMethods.contains("if (!ANIMATION.observeProxyFrame"));
    }

    @Test
    public void nativeShortcutIconIsReadOnlyDuringGlassAnimation() throws Exception {
        String registry = Files.readString(MAIN.resolve("DockGlassItemRegistry.java"));
        String compositor = Files.readString(MAIN.resolve("DockGlassCompositor.java"));
        String animationMethods = slice(registry,
                "static synchronized void observeLaunchAnimationFrame",
                "static synchronized float animationOpacity");

        assertFalse("glass animation must never schedule redraws on the native ShortcutIcon",
                animationMethods.contains("view.postInvalidateOnAnimation()"));
        assertFalse("compositor must never use a native icon View as its frame clock",
                compositor.contains("itemView.postInvalidateOnAnimation()"));
        assertTrue("animation state changes must wake LiquidDock's own renderer",
                animationMethods.contains("Miuix307ZeroCopyRenderer.requestDockAnimationFrames();"));
    }

    @Test
    public void zeroCopyRendererOwnsFadeFrameScheduling() throws Exception {
        String registry = Files.readString(MAIN.resolve("DockGlassItemRegistry.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue("registry must report whether a Dock glass fade is still active",
                registry.contains("static synchronized boolean hasActiveAnimation()"));
        assertTrue("the LiquidDock TextureView must own the vsync callback",
                renderer.contains("gpuBackdrop.postOnAnimation("));
        assertTrue("the frame pump must stop when no Dock glass fade remains",
                renderer.contains("DockGlassItemRegistry.hasActiveAnimation()"));
        assertTrue("each owned animation frame must refresh only the LiquidDock scene",
                renderer.contains("gpuBackdrop.requestDockSceneRefresh();"));
    }

    @Test
    public void floatingProxyStateIsObservedAfterVendorUpdate() throws Exception {
        String hook = Files.readString(MAIN.resolve("DockIconAnimationGlassHook.java"));
        String proxyHook = slice(hook,
                "private static boolean installFloatingProxyHook",
                "}\n}");
        int vendorProceed = proxyHook.indexOf("Object result = chain.proceed(args);");
        int observe = proxyHook.indexOf("DockGlassItemRegistry.observeLaunchAnimationFrame(");

        assertTrue("vendor update must commit before LiquidDock samples the handoff state",
                vendorProceed >= 0 && observe > vendorProceed);
        assertTrue("proxy hook must return the already-completed vendor result",
                proxyHook.contains("return result;"));
    }

    @Test
    public void lateBackAnimStopCannotHideDockSourceAfterVendorRestore() throws Exception {
        String hook = Files.readString(MAIN.resolve("DockIconAnimationGlassHook.java"));
        String visibilityHook = slice(hook,
                "private static boolean installShortcutVisibilityHook",
                "private static boolean installFloatingProxyHook");

        assertTrue("the hook must remember when MIUI formally returns native source ownership",
                hook.contains("SOURCE_RETURNED"));
        assertTrue("VISIBLE setAnimTargetVisibility must mark the source as returned",
                visibilityHook.contains("View.VISIBLE")
                        && visibilityHook.contains("SOURCE_RETURNED.put("));
        assertTrue("ShortcutIcon.onBackAnimStop must be observed as a separate vendor end lifecycle",
                hook.contains("\"onBackAnimStop\""));
        assertTrue("a late onBackAnimStop hide must be repaired through the direct icon drawable API",
                hook.contains("\"setIconVisibility\"")
                        && hook.contains("View.VISIBLE"));
        assertFalse("late-hide repair must not recurse through setAnimTargetVisibility",
                lateHideGuardSlice(hook).contains("setAnimTargetVisibility"));
    }

    @Test
    public void membershipRevisionRemainsOwnedByRegistryMembershipChanges() throws Exception {
        String registry = Files.readString(MAIN.resolve("DockGlassItemRegistry.java"));

        assertTrue(registry.contains("private static long membershipRevision;"));
        assertTrue(registry.contains("static synchronized long revision() { return membershipRevision; }"));
    }

    @Test
    public void compositorSamplesAnimationOnceAndReusesStableGeometry() throws Exception {
        String node = Files.readString(MAIN.resolve("DockGlassItemNode.java"));
        String compositor = Files.readString(MAIN.resolve("DockGlassCompositor.java"));

        assertTrue(node.contains("DockIconAnimationState.Sample animationSample(long nowMs)"));
        assertTrue(compositor.contains("private static final class CachedItem"));
        assertTrue(compositor.contains("LauncherGlassGeometry.Snapshot geometry;"));
        assertTrue(compositor.contains("DockIconAnimationState.Sample[] animationSamples"));
        assertTrue(compositor.contains("geometryMappingChanged || cachedItem.uiFingerprint != uiFingerprint"));
        assertFalse(compositor.contains("item.animationOpacity(nowMs)"));
        assertFalse(compositor.contains("item.isFading()"));
    }

    @Test
    public void vendorBlurSuppressionWritesRadiusZeroOnlyOncePerPredraw() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        String suppression = slice(hook,
                "static void suppressVendorGpuBlur",
                "private static void installVendorGpuBlurSuppressor");

        assertFalse(suppression.contains("setPassWindowBlurRadius(dockBg, 0)"));
        assertTrue(suppression.contains("MiBlurBridge.clearPassWindowBlur(dockBg);"));
    }

    private static String lateHideGuardSlice(String source) {
        int from = source.indexOf("onBackAnimStop");
        int to = source.indexOf("private static boolean installFloatingProxyHook", from);
        if (from < 0 || to < 0) return source;
        return source.substring(from, to);
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        if (from < 0 || to < 0) throw new AssertionError("source anchors unavailable");
        return source.substring(from, to);
    }
}
