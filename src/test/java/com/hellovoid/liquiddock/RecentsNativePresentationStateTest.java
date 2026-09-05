package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Recents owns presentation by covering the Launcher root, never by hiding cached glass. */
public class RecentsNativePresentationStateTest {
    @Test
    public void recentsCaptureCoverPreservesCachedLayerPresentation() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());

        state.setCovered(true);

        assertEquals(LauncherGlassSceneController.State.COVERED, state.state());
        assertTrue("Recents may block capture but must leave cached glass under MIUI's native layer",
                state.isLayerVisible());
        assertFalse("Recents cover must not schedule a LiquidDock-owned presentation fade",
                state.consumeFadeReveal());
    }

    @Test
    public void recentsReturnKeepsCachedLayerVisibleWhileWaitingForFreshFrame() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        state.setCovered(true);
        long coveredGeneration = state.generation();

        state.setCovered(false);

        assertEquals(LauncherGlassSceneController.State.HOME_WAITING_FRESH_FRAME, state.state());
        assertEquals(coveredGeneration + 1L, state.generation());
        assertTrue("MIUI Recents fade-out must expose the same cached glass as Workspace content",
                state.isLayerVisible());
        assertFalse("semantic Recents hide must not restart a glass fade",
                state.consumeFadeReveal());

        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
        assertFalse("fresh capture replaces content without a second presentation fade",
                state.consumeFadeReveal());
    }
}
