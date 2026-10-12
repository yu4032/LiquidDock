package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

/** Runtime state contracts for hiding, refreshing and restoring Workspace glass. */
public class WorkspaceRenderPipelineFixContractTest {
    @Test public void appCoverWithNoOtherConsumersStopsOnlyHiddenHome() {
        assertFalse(WorkspaceRenderEligibilityPolicy.renderHome(true, false));
        assertTrue(WorkspaceRenderEligibilityPolicy.pauseProducerForAppCover(
                true, false, false));
        assertFalse(WorkspaceRenderEligibilityPolicy.normalizeInput(
                true, false, false));
    }

    @Test public void visibleRecentsContinuesToReceiveSharedSourceWithoutDrawingHome() {
        assertFalse(WorkspaceRenderEligibilityPolicy.renderHome(true, false));
        assertTrue(WorkspaceRenderEligibilityPolicy.normalizeInput(
                true, false, true));
        assertFalse(WorkspaceRenderEligibilityPolicy.pauseProducerForAppCover(
                true, false, true));
    }

    @Test public void appResumeCanRetainCachedGlassAndRequireFreshGeneration() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onBootstrapReconciled();
        state.onFreshFrameReady(state.generation());
        assertTrue(state.isLayerVisible());
        long priorGeneration = state.generation();

        // App coverage itself need not clear the correctly presented buffer.
        state.onPresentationStarted();
        assertTrue(state.isLayerVisible());
        assertFalse(state.shouldRequestFreshAfterPresentation());

        // Uncover invalidates wallpaper/source authority; the last buffer is
        // retained until the matching fresh frame arrives.
        state.onGenerationInvalidated();
        assertTrue(state.generation() > priorGeneration);
        assertTrue(state.isLayerVisible());
        assertTrue(state.shouldRequestFreshAfterPresentation());
        state.onFreshFrameReady(priorGeneration);
        assertTrue(state.shouldRequestFreshAfterPresentation());
        state.onFreshFrameReady(state.generation());
        assertFalse(state.shouldRequestFreshAfterPresentation());
    }

    @Test public void lastGlassNodeDisappearingMustPresentOneTransparentFrame() {
        assertTrue(WorkspaceStaticOutputPolicy.shouldRender(false, false));
        assertFalse(WorkspaceStaticOutputPolicy.shouldRender(true, false));
        assertTrue(WorkspaceStaticOutputPolicy.shouldRender(true, true));
    }

    @Test public void workstationContinuesIndependentRenderingOnActivityStop() {
        assertTrue(WorkspaceRenderEligibilityPolicy.renderHome(true, true));
        assertTrue(WorkspaceRenderEligibilityPolicy.normalizeInput(true, true, false));
        assertFalse(WorkspaceRenderEligibilityPolicy.pauseProducerForAppCover(
                true, true, false));
    }
}
