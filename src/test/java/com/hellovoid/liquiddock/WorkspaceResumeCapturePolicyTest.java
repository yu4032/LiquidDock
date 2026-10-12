package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class WorkspaceResumeCapturePolicyTest {
    @Test public void appResumeDoesNotCaptureDuringFirstNativeFrame() {
        assertEquals(WorkspaceResumeCapturePolicy.Decision.NEXT_FRAME,
                WorkspaceResumeCapturePolicy.onFrame(2, false, true));
    }

    @Test public void nonSpringResumesRequestFreshOnNextOpportunity() {
        assertEquals(WorkspaceResumeCapturePolicy.Decision.REQUEST_FRESH,
                WorkspaceResumeCapturePolicy.onFrame(1, false, true));
    }

    @Test public void realHomeSpringOwnsCaptureEvenAfterTwoOpportunities() {
        assertEquals(WorkspaceResumeCapturePolicy.Decision.DEFER_TO_PRESENTATION,
                WorkspaceResumeCapturePolicy.onFrame(2, true, true));
        assertEquals(WorkspaceResumeCapturePolicy.Decision.DEFER_TO_PRESENTATION,
                WorkspaceResumeCapturePolicy.onFrame(1, true, true));
    }

    @Test public void alreadyFreshSceneNeverStartsRedundantCapture() {
        assertEquals(WorkspaceResumeCapturePolicy.Decision.NO_REQUEST,
                WorkspaceResumeCapturePolicy.onFrame(1, false, false));
    }

    @Test public void oldFrameFromResumeCannotCompletePostSpringGeneration() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onBootstrapReconciled();
        state.onFreshFrameReady(state.generation());
        state.onGenerationInvalidated(); // App's onResume
        long early = state.generation();
        state.onAppReturnPresentationStarted(); // Accepted native HOME spring
        assertTrue(state.generation() > early);
        state.onFreshFrameReady(early); // Late OES frame from discarded request
        assertTrue(state.shouldRequestFreshAfterPresentation());
        state.onFreshFrameReady(state.generation());
        assertFalse(state.shouldRequestFreshAfterPresentation());
    }
}
