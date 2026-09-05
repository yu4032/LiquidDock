package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure behavior coverage for the Workspace scene visibility/freshness state machine. */
public class LauncherGlassSceneControllerTest {
    @Test
    public void coveredSceneRequiresFreshFrameBeforeReveal() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        long first = state.generation();
        assertFalse(state.isLayerVisible());
        state.onFreshFrameReady(first);
        assertTrue(state.isLayerVisible());

        state.setCovered(true);
        assertFalse(state.isLayerVisible());
        state.setCovered(false);
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
    public void onlyFirstFreshFrameAfterCoverageRequestsFadeReveal() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        assertTrue(state.consumeFadeReveal());

        state.setCovered(true);
        state.setCovered(false);
        state.onFreshFrameReady(state.generation());
        assertTrue(state.consumeFadeReveal());
        assertFalse(state.consumeFadeReveal());

        state.onGenerationInvalidated();
        state.onFreshFrameReady(state.generation());
        assertFalse(state.consumeFadeReveal());
    }

    @Test
    public void acceptedWorkstationRolloverStillWaitsForFreshSceneGeneration() {
        WorkstationRecentsRecoveryPolicy.Decision recovery =
                WorkstationRecentsRecoveryPolicy.onRecentsReturn(true, true);
        assertTrue(recovery.allowUncover);

        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());

        state.setCovered(true);
        if (recovery.allowUncover) state.setCovered(false);
        assertFalse("endpoint rollover acceptance is not a fresh frame", state.isLayerVisible());

        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
    }
}
