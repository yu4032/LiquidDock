package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests presentation vs. source freshness independently.
 * Device GPU sampling and eglSwapBuffers still require Perfetto evidence.
 */
public class WorkspaceAppReturnCaptureContractTest {
    private static LauncherGlassSceneController.StateMachine visibleHome() {
        LauncherGlassSceneController.StateMachine s =
                new LauncherGlassSceneController.StateMachine();
        s.onRootReady();
        s.onBootstrapReconciled();
        s.onFreshFrameReady(s.generation());
        s.consumeFadeReveal();
        return s;
    }

    @Test public void appHomePresentationReusesVisibleCacheWithoutInvalidatingSource() {
        LauncherGlassSceneController.StateMachine s = visibleHome();
        long generation = s.generation();
        s.onPresentationStarted();
        assertTrue("Presentation alone must not hide cached glass", s.isLayerVisible());
        assertEquals("Presentation alone must not request a new generation",
                generation, s.generation());
        assertFalse("Do not force a new source frame solely for scaling",
                s.shouldRequestFreshAfterPresentation());
        assertFalse("A cached layer should not restart reveal fade",
                s.consumeFadeReveal());
    }

    @Test public void realSourceInvalidationStillRequiresFreshFrame() {
        LauncherGlassSceneController.StateMachine s = visibleHome();
        long stale = s.generation();
        s.onPresentationStarted();
        s.onGenerationInvalidated();
        assertTrue(s.shouldRequestFreshAfterPresentation());
        assertTrue("Keep existing cache until replacement is ready", s.isLayerVisible());
        s.onFreshFrameReady(stale);
        assertTrue(s.shouldRequestFreshAfterPresentation());
        s.onFreshFrameReady(s.generation());
        assertFalse(s.shouldRequestFreshAfterPresentation());
        assertTrue(s.isLayerVisible());
    }

    @Test public void recentsCoverageMayKeepCacheButRequiresReplacementOnReturn() {
        LauncherGlassSceneController.StateMachine s = visibleHome();
        long old = s.generation();
        s.setCovered(true);
        assertTrue("Recents cover is capture-only", s.isLayerVisible());
        s.setCovered(false);
        assertTrue(s.generation() > old);
        assertTrue(s.shouldRequestFreshAfterPresentation());
        assertTrue(s.isLayerVisible());
        s.onFreshFrameReady(s.generation());
        assertFalse(s.shouldRequestFreshAfterPresentation());
    }

    @Test public void folderHardCoverIsNotEquivalentToZeroAlphaCaching() {
        LauncherGlassSceneController.StateMachine s = visibleHome();
        s.setHardCovered(true);
        assertFalse(s.isLayerVisible());
        s.setHardCovered(false);
        assertFalse(s.isLayerVisible());
        assertTrue(s.shouldRequestFreshAfterPresentation());
        s.onFreshFrameReady(s.generation());
        assertTrue(s.isLayerVisible());
        assertTrue(s.consumeFadeReveal());
    }
}
