package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Capture barriers may invalidate freshness without owning cached Workspace presentation. */
public class HomeCaptureBarrierStateTest {
    @Test
    public void homeCaptureInvalidationKeepsCachedLayerVisible() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
        state.consumeFadeReveal(); // settle the initial bootstrap presentation before this scenario.

        state.onGenerationInvalidated();

        assertEquals(LauncherGlassSceneController.State.HOME_WAITING_FRESH_FRAME, state.state());
        assertTrue("HOME capture invalidation must not create a LiquidDock presentation edge",
                state.isLayerVisible());
        assertFalse(state.consumeFadeReveal());

        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
        assertFalse("fresh replacement must not replay a reveal for an already-visible cache",
                state.consumeFadeReveal());
    }

    @Test
    public void hardFolderCoverStillWaitsForFreshFrameBeforeReveal() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());

        state.setHardCovered(true);
        assertEquals(LauncherGlassSceneController.State.COVERED, state.state());
        assertFalse(state.isLayerVisible());

        state.setHardCovered(false);
        assertEquals(LauncherGlassSceneController.State.HOME_WAITING_FRESH_FRAME, state.state());
        assertFalse(state.isLayerVisible());

        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
        assertTrue("hard presentation cover retains the existing fresh-frame fade-in rule",
                state.consumeFadeReveal());
    }

    @Test
    public void recentsCaptureCoverPreservesPendingHardCoverReveal() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        state.consumeFadeReveal();

        state.setHardCovered(true);
        state.setHardCovered(false);
        assertFalse(state.isLayerVisible());

        // Recents may remain/arrive while the hard cover is waiting for a fresh HOME scene.
        // Its capture-only coverage must not erase that pending hard-cover presentation recovery.
        state.setCovered(true);
        state.setCovered(false);
        state.onFreshFrameReady(state.generation());

        assertTrue(state.isLayerVisible());
        assertTrue("Recents capture coverage must preserve the hard-cover fresh reveal intent",
                state.consumeFadeReveal());
    }
}
