package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Regression coverage for widget <-> app glass ownership and fresh-frame gating. */
public class LauncherWidgetTransitionStateTest {
    @Test
    public void launchFadeRetainsGeometryUntilFadeEnds() {
        LauncherWidgetTransitionState state = new LauncherWidgetTransitionState();
        assertFalse(state.shouldRetainGeometry());

        state.beginLaunchFadeOut();
        assertTrue(state.isLaunchFadeOut());
        assertTrue(state.shouldRetainGeometry());

        state.finishLaunchFadeOut();
        assertFalse(state.isLaunchFadeOut());
        assertFalse(state.shouldRetainGeometry());
    }

    @Test
    public void widgetReturnWaitsForMatchingFreshGeneration() {
        LauncherWidgetTransitionState state = new LauncherWidgetTransitionState();
        state.beginReturnWaitingFresh(42L);

        assertTrue(state.isReturnWaitingFresh());
        assertTrue(state.shouldRetainGeometry());
        assertFalse(state.onFreshFrame(41L));
        assertTrue(state.isReturnWaitingFresh());

        assertTrue(state.onFreshFrame(42L));
        assertTrue(state.isReturnFadeIn());
        assertTrue(state.shouldRetainGeometry());

        // The same fresh frame must not start a second fade.
        assertFalse(state.onFreshFrame(42L));
        state.finishReturnFadeIn();
        assertFalse(state.shouldRetainGeometry());
    }

    @Test
    public void newerReturnSupersedesOlderPendingGeneration() {
        LauncherWidgetTransitionState state = new LauncherWidgetTransitionState();
        state.beginReturnWaitingFresh(7L);
        state.beginReturnWaitingFresh(9L);

        assertFalse(state.onFreshFrame(7L));
        assertTrue(state.onFreshFrame(9L));
    }
}
