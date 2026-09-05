package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure behavior coverage for Workspace presentation/freshness ownership. */
public class LauncherGlassSceneControllerTest {
    @Test
    public void hardCoveredSceneRequiresFreshFrameBeforeReveal() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        long first = state.generation();
        assertFalse(state.isLayerVisible());
        state.onFreshFrameReady(first);
        assertTrue(state.isLayerVisible());

        state.setHardCovered(true);
        assertFalse(state.isLayerVisible());
        state.setHardCovered(false);
        assertFalse(state.isLayerVisible());
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
    }

    @Test
    public void staleGenerationCannotRevealLayer() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        long stale = state.generation();
        state.onGenerationInvalidated();
        assertTrue(state.generation() > stale);
        state.onFreshFrameReady(stale);
        assertFalse(state.isLayerVisible());
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
    }

    @Test
    public void onlyFirstFreshFrameAfterHardCoverageRequestsFadeReveal() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        assertTrue(state.consumeFadeReveal());

        state.setHardCovered(true);
        state.setHardCovered(false);
        state.onFreshFrameReady(state.generation());
        assertTrue(state.consumeFadeReveal());
        assertFalse(state.consumeFadeReveal());

        state.onGenerationInvalidated();
        state.onFreshFrameReady(state.generation());
        assertFalse(state.consumeFadeReveal());
    }

    @Test
    public void acceptedWorkstationRolloverStillWaitsForFreshSceneWithoutHidingCache() {
        WorkstationRecentsRecoveryPolicy.Decision recovery =
                WorkstationRecentsRecoveryPolicy.onRecentsReturn(true, true);
        assertTrue(recovery.allowUncover);

        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
        state.consumeFadeReveal();

        state.setCovered(true);
        assertEquals(LauncherGlassSceneController.State.COVERED, state.state());
        assertTrue("Recents capture cover leaves cached glass under MIUI's native layer",
                state.isLayerVisible());

        if (recovery.allowUncover) state.setCovered(false);
        assertEquals(LauncherGlassSceneController.State.HOME_WAITING_FRESH_FRAME, state.state());
        assertTrue("endpoint rollover acceptance is not a fresh frame, but must not hide the cache",
                state.isLayerVisible());
        assertFalse(state.consumeFadeReveal());

        state.onFreshFrameReady(state.generation());
        assertEquals(LauncherGlassSceneController.State.HOME_VISIBLE, state.state());
        assertTrue(state.isLayerVisible());
        assertFalse("fresh Workstation recovery replaces content without a second visual reveal",
                state.consumeFadeReveal());
    }
}
