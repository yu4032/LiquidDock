package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class LauncherGlassWorkspaceScrollSyncContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void staticGlassHooksVendorScreenViewScrollMutation() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));

        assertTrue(hook.contains("\"com.miui.home.launcher.ScreenView\", \"scrollTo\""));
        assertTrue(hook.contains("LauncherGlassStaticLayer.onWorkspaceScrollMutation"));
        assertFalse(hook.contains("WorkspaceScrollMotionTracker"));
    }

    @Test
    public void compensationIsLateLatchedAtTextureFrameBoundary() throws Exception {
        String layer = Files.readString(MAIN.resolve("LauncherGlassStaticLayer.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(layer.contains("LauncherGlassScrollCompensationState"));
        assertTrue(layer.contains("onSurfaceTextureUpdated"));
        assertTrue(layer.contains("onStaticFrameAnchorQueued"));
        assertTrue(session.contains("queueStaticFrameAnchor"));
    }

    @Test
    public void scrollPathDoesNotTriggerFullGeometryRecapture() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));

        int start = hook.indexOf("installWorkspaceScrollCompensationHook");
        assertTrue(start >= 0);
        String tail = hook.substring(start, Math.min(hook.length(), start + 5000));
        assertFalse(tail.contains("captureGeometry"));
        assertFalse(tail.contains("reconcileCurrentWorkspacePage"));
        assertFalse(tail.contains("requestFreshForRoot"));
    }
}
