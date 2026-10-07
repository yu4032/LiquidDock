package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** HOME refresh must keep the producer live through the full return animation. */
public class DockHomeBackdropFreshnessStateTest {
    @Test public void homeStartImmediatelyForcesFreshProducerWithoutBlockingPresentation() {
        DockHomeBackdropFreshnessState state = new DockHomeBackdropFreshnessState();

        DockHomeBackdropFreshnessState.Decision start = state.onHomeStarted(7L);
        assertTrue(start.forceProducerUpdates);
        assertFalse(start.blockPresentation);
        assertFalse(start.releaseProducerOverride);
    }

    @Test public void freshFrameBeforeFinishDoesNotReleaseProducerEarly() {
        DockHomeBackdropFreshnessState state = new DockHomeBackdropFreshnessState();
        state.onHomeStarted(21L);

        DockHomeBackdropFreshnessState.Decision fresh = state.onProducerFrameAvailable();
        assertFalse(fresh.releasePresentation);
        assertFalse(fresh.releaseProducerOverride);

        DockHomeBackdropFreshnessState.Decision finish = state.onHomeFinished(21L);
        assertTrue(finish.releaseProducerOverride);
    }

    @Test public void finishBeforeFreshFrameWaitsForRealProducerPresentation() {
        DockHomeBackdropFreshnessState state = new DockHomeBackdropFreshnessState();
        state.onHomeStarted(31L);

        DockHomeBackdropFreshnessState.Decision finish = state.onHomeFinished(31L);
        assertFalse(finish.releaseProducerOverride);

        DockHomeBackdropFreshnessState.Decision fresh = state.onProducerFrameAvailable();
        assertTrue(fresh.releaseProducerOverride);
    }

    @Test public void newerHomeStartSupersedesOlderTransition() {
        DockHomeBackdropFreshnessState state = new DockHomeBackdropFreshnessState();
        state.onHomeStarted(40L);
        DockHomeBackdropFreshnessState.Decision newer = state.onHomeStarted(41L);

        assertTrue(newer.forceProducerUpdates);
        assertFalse(state.onHomeFinished(40L).releaseProducerOverride);
        assertFalse(state.onProducerFrameAvailable().releaseProducerOverride);
        assertTrue(state.onHomeFinished(41L).releaseProducerOverride);
    }
}
