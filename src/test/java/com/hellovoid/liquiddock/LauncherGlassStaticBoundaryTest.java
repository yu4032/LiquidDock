package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static-only boundaries: process scope and forbidden capture/reflection APIs. */
public class LauncherGlassStaticBoundaryTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path SCOPE =
            Path.of("src/main/resources/META-INF/xposed/scope.list");

    @Test
    public void xposedScopeIncludesLauncherAndSystemUi() throws Exception {
        String scope = Files.readString(SCOPE);
        assertTrue(scope.contains("com.miui.home"));
        assertTrue(scope.contains("com.android.systemui"));
    }

    @Test
    public void systemUiObserverDoesNotOwnScreenCaptureOrPassBlurSurface() throws Exception {
        String source = Files.readString(MAIN.resolve("SystemUiKeyguardGoneSource.java"));
        assertFalse(source.contains("ScreenCapture"));
        assertFalse(source.contains("captureDisplay"));
        assertFalse(source.contains("import android.view.SurfaceControl"));
        assertFalse(source.contains("SetPassBlurSurface"));
    }

    @Test
    public void registryDoesNotReflectIntoProjectOwnedSession() throws Exception {
        String registry = Files.readString(MAIN.resolve("LauncherGlassSessionRegistry.java"));
        assertFalse(registry.contains("HookUtil"));
    }

    @Test
    public void workspaceScrollLateLatchKeepsBackdropRootAnchored() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        String layer = Files.readString(MAIN.resolve("LauncherGlassStaticLayer.java"));
        String node = Files.readString(MAIN.resolve("LauncherGlassStaticNode.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(hook.contains("\"com.miui.home.launcher.ScreenView\""));
        assertTrue(hook.contains("getDeclaredMethod(\"scrollTo\", int.class, int.class)"));
        assertTrue(hook.contains("LauncherGlassStaticLayer.onWorkspaceScrollMutation"));

        // A root-wide TextureView transform moves the already sampled backdrop together with the
        // glass geometry. That breaks behind-content correspondence for fixed/parallax wallpaper.
        assertFalse(layer.contains("import android.graphics.Matrix"));
        assertFalse(layer.contains("setTransform("));
        assertTrue(layer.contains("session.onWorkspaceScrollMutation"));

        // Static glass geometry, unlike the root-wide sampled backdrop, must inherit the live
        // Workspace ancestor matrix. This is what lets cached HOME glass ride MIUI's unlock scale.
        assertTrue(node.contains("material.transformMatrixToGlobal(materialToGlobal)"));
        assertTrue(node.contains("root.transformMatrixToGlobal(rootToGlobal)"));
        assertTrue(session.contains("ViewTreeObserver.OnPreDrawListener listener"));
        assertTrue(session.contains("node.captureGeometry(root)"));

        // This is retained legacy source-reader debt. New runtime callback/freshness contracts
        // belong in typed production state tests rather than method-body/source-order assertions.
        assertTrue(session.contains("onWorkspaceScrollMutation"));
        assertTrue(session.contains("workspaceScrollProjection"));
        assertTrue(session.contains("projectCenterX"));
        assertTrue(session.contains("requestStaticRedraw"));
        assertTrue(session.contains("StaticGeometryFrame"));

        assertFalse(hook.contains("WorkspaceScrollMotionTracker"));
        assertFalse(layer.contains("WorkspaceScrollMotionTracker"));
    }
}
