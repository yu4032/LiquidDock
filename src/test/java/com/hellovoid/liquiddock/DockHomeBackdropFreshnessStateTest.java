package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** HOME refresh must start immediately without ever hiding the Dock backdrop. */
public class DockHomeBackdropFreshnessStateTest {
    @Test public void homeStartImmediatelyForcesFreshProducerWithoutBlockingPresentation() {
        DockHomeBackdropFreshnessState state = new DockHomeBackdropFreshnessState();

        DockHomeBackdropFreshnessState.Decision start = state.onHomeStarted(7L);
        assertTrue(start.forceProducerUpdates);
        assertFalse(start.blockPresentation);
        assertFalse(start.releaseProducerOverride);
    }

    @Test public void sceneOnlyTextureViewSwapCannotSatisfyHomeProducerFreshness() {
        // A scene-only redraw can advance the output timestamp but cannot
        // increment the input callback generation or the presented generation.
        long baseline=23L;
        assertFalse(DockHomeBackdropFreshnessState.isNewProducerFramePresented(baseline,22L));
        assertFalse(DockHomeBackdropFreshnessState.isNewProducerFramePresented(baseline,23L));
        // A newer callback with no EGL presentation is still not sufficient.
        assertFalse(DockHomeBackdropFreshnessState.isNewProducerFramePresented(baseline,23L));
        assertTrue(DockHomeBackdropFreshnessState.isNewProducerFramePresented(baseline,24L));
    }

    @Test public void firstFrameAfterHomeStartReleasesProducerOverrideExactlyOnce() {
        DockHomeBackdropFreshnessState state = new DockHomeBackdropFreshnessState();
        state.onHomeStarted(21L);

        DockHomeBackdropFreshnessState.Decision first = state.onProducerFrameAvailable();
        assertFalse(first.releasePresentation);
        assertTrue(first.releaseProducerOverride);

        DockHomeBackdropFreshnessState.Decision second = state.onProducerFrameAvailable();
        assertFalse(second.releasePresentation);
        assertFalse(second.releaseProducerOverride);
    }

    @Test public void finishNeverArmsAVisibilityBarrier() {
        DockHomeBackdropFreshnessState state = new DockHomeBackdropFreshnessState();
        state.onHomeStarted(31L);

        DockHomeBackdropFreshnessState.Decision finish = state.onHomeFinished(31L);
        assertFalse(finish.blockPresentation);
        assertFalse(finish.releasePresentation);
        assertFalse(finish.forceProducerUpdates);
    }

    @Test public void newerHomeStartSupersedesOlderTransition() {
        DockHomeBackdropFreshnessState state = new DockHomeBackdropFreshnessState();
        state.onHomeStarted(40L);
        DockHomeBackdropFreshnessState.Decision newer = state.onHomeStarted(41L);

        assertTrue(newer.forceProducerUpdates);
        assertFalse(state.onHomeFinished(40L).forceProducerUpdates);
        assertTrue(state.onProducerFrameAvailable().releaseProducerOverride);
    }
}
