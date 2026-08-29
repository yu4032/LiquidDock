package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source/proxy ownership contracts, including the one-frame handoff A/B probe. */
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
    public void closeToHomeFinalProxyFramePrimesNativeSourceBeforeProxyTeardown() throws Exception {
        String hook = Files.readString(MAIN.resolve("DockIconAnimationGlassHook.java"));
        String proxyHook = slice(hook,
                "private static boolean installFloatingProxyHook",
                "private static void primeNativeSourceForHandoff");
        String handoff = slice(hook,
                "private static void primeNativeSourceForHandoff",
                "}\n}");

        assertTrue("pre-roll must wait until MIUI reports final proxy geometry",
                hook.contains("SOURCE_PRIME_PROGRESS = 1.0f"));
        assertTrue("the final CLOSE_TO_HOME proxy frame must pre-roll the native source",
                proxyHook.contains("primeNativeSourceForHandoff((View) target, progress);"));
        assertTrue("pre-roll must reveal the actual ShortcutIcon content, not recurse through the anim target API",
                handoff.contains("\"setIconVisibility\", new Class<?>[]{int.class}")
                        && handoff.contains("View.VISIBLE"));
        assertFalse("handoff pre-roll must not recurse through setAnimTargetVisibility",
                handoff.contains("setAnimTargetVisibility"));
        assertTrue("each native source may only be primed once per handoff",
                handoff.contains("HANDOFF_PRIMED.containsKey(target)"));
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

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        if (from < 0 || to < 0) throw new AssertionError("source anchors unavailable");
        return source.substring(from, to);
    }
}
