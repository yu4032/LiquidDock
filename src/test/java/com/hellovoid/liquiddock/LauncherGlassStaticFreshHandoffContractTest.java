package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Typed state contract for the cold-start frame-before-output handoff. */
public class LauncherGlassStaticFreshHandoffContractTest {
    @Test
    public void matchingGenerationIsRetainedUntilStaticOutputCanPresent() {
        LauncherGlassStaticFreshHandoffState state =
                new LauncherGlassStaticFreshHandoffState();

        state.record(7L, 3L, true);
        LauncherGlassStaticFreshHandoffState.Pending pending = state.pendingFor(7L);

        assertTrue(pending.ready());
        assertEquals(7L, pending.generation);
        assertEquals(3L, pending.wallpaperGeneration);
        assertTrue(pending.wallpaperAuthoritative);
    }

    @Test
    public void staleGenerationCannotBePresentedIntoCurrentScene() {
        LauncherGlassStaticFreshHandoffState state =
                new LauncherGlassStaticFreshHandoffState();

        state.record(6L, 2L, false);
        LauncherGlassStaticFreshHandoffState.Pending pending = state.pendingFor(7L);

        assertFalse(pending.ready());
    }

    @Test
    public void clearDropsPreviouslyRetainedFreshAuthority() {
        LauncherGlassStaticFreshHandoffState state =
                new LauncherGlassStaticFreshHandoffState();

        state.record(7L, 3L, true);
        state.clear();

        assertFalse(state.pendingFor(7L).ready());
    }
}
