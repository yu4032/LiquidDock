package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Requires the clip to be a strictly visual HWUI optimization. EGL Surface coordinates,
 * PassBlur freshness, Wallpaper and root-wide GL drawing remain unchanged.
 */
public class LauncherGlassCompositorClipBoundaryTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock", file));
    }

    @Test public void compositorClipMustNotResizeOrTranslateRootTexture() throws Exception {
        String layer = read("LauncherGlassStaticLayer.java");
        assertTrue(layer.contains("setOpaque(false)"));
        assertTrue(layer.contains("ViewGroup.LayoutParams.MATCH_PARENT"));
        assertTrue(layer.contains("setClipBounds(compositeClip)"));
        assertTrue(layer.contains("setClipBounds(null)"));
        assertTrue(layer.contains("layer.applyCompositorClip(null)"));
        assertFalse(layer.contains("setTransform("));
        assertFalse(layer.contains("setDefaultBufferSize("));
    }

    @Test public void motionAndLifecycleAlwaysClearVisualClip() throws Exception {
        String session = read("LauncherGlassSession.java");
        String layer = read("LauncherGlassStaticLayer.java");
        assertTrue(session.contains("invalidateStaticCompositorClip();"));
        assertTrue(session.contains("geometryMotionChanged || staticChanged || rotationSettlePending"));
        assertTrue(session.contains("nextRotation != configRotation"));
        assertTrue(session.contains("LauncherGlassSceneController.isRecentsCoveredByVendor()"));
        assertTrue(session.contains("staticClipStableObservations < 1"));
        assertTrue(session.contains("workspaceScrollProjection.projectCenterX("));
        assertTrue(layer.contains("applyCompositorClip(null);"));
    }

    @Test public void frameAndEglTraceDoNotRequireSynchronousDebugLogging() throws Exception {
        String session = read("LauncherGlassSession.java");
        String backend = read("RootPassBlurBackend.java");
        assertTrue(session.contains("Trace.beginSection(\"LD.Workspace.SurfaceClear\")"));
        assertTrue(session.contains("Trace.beginSection(\"LD.Workspace.StaticDraw\")"));
        assertTrue(session.contains("Trace.beginSection(\"LD.Workspace.StaticSwap\")"));
        assertTrue(session.contains("LD.Workspace.StaticSurfacePixels"));
        assertTrue(session.contains("LD.Workspace.HwuiClipPixels"));
        assertTrue(backend.contains("Trace.beginSection(\"LD.Workspace.EglSwap\")"));
        assertTrue(backend.contains("EGL_EXT_buffer_age"));
        assertTrue(backend.contains("BUFFER_AGE_SAMPLE_INTERVAL = 60"));
        assertFalse(backend.contains("eglSwapBuffersWithDamage"));
    }
}
