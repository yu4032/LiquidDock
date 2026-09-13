package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** HOME presentation must wait for a producer frame newer than the matching FINISH boundary. */
public class DockHomeBackdropFreshnessStateTest {
    @Test public void frameBeforeHomeFinishCannotReleasePresentation() {
        DockHomeBackdropFreshnessState state = new DockHomeBackdropFreshnessState();

        DockHomeBackdropFreshnessState.Decision start = state.onHomeStarted(7L);
        assertTrue(start.blockPresentation);

        DockHomeBackdropFreshnessState.Decision duringTransition = state.onProducerFrameAvailable();
        assertFalse(duringTransition.releasePresentation);

        DockHomeBackdropFreshnessState.Decision finish = state.onHomeFinished(7L);
        assertTrue(finish.forceProducerUpdates);
        assertFalse(finish.releasePresentation);

        DockHomeBackdropFreshnessState.Decision postFinishFrame = state.onProducerFrameAvailable();
        assertTrue(postFinishFrame.releasePresentation);
        assertTrue(postFinishFrame.releaseProducerOverride);
    }

    @Test public void staleFinishCannotArmFreshnessForNewerHomeTransition() {
        DockHomeBackdropFreshnessState state = new DockHomeBackdropFreshnessState();
        state.onHomeStarted(10L);
        state.onHomeStarted(11L);

        DockHomeBackdropFreshnessState.Decision staleFinish = state.onHomeFinished(10L);
        assertFalse(staleFinish.forceProducerUpdates);

        DockHomeBackdropFreshnessState.Decision matchingFinish = state.onHomeFinished(11L);
        assertTrue(matchingFinish.forceProducerUpdates);
        assertFalse(state.onProducerFrameAvailable().forceProducerUpdates);
        assertTrue(state.onProducerFrameAvailable().releasePresentation == false);
    }

    @Test public void firstFrameAfterMatchingFinishReleasesExactlyOnce() {
        DockHomeBackdropFreshnessState state = new DockHomeBackdropFreshnessState();
        state.onHomeStarted(21L);
        state.onHomeFinished(21L);

        DockHomeBackdropFreshnessState.Decision first = state.onProducerFrameAvailable();
        assertTrue(first.releasePresentation);
        assertTrue(first.releaseProducerOverride);

        DockHomeBackdropFreshnessState.Decision second = state.onProducerFrameAvailable();
        assertFalse(second.releasePresentation);
        assertFalse(second.releaseProducerOverride);
    }
}
