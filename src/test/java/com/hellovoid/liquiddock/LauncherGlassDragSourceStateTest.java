package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LauncherGlassDragSourceStateTest {
    @Test
    public void firstUnsafeDragOwnsProducerPause() {
        LauncherGlassDragSourceState state = new LauncherGlassDragSourceState();

        assertTrue(state.begin());
        assertTrue(state.isBlocked());
    }

    @Test
    public void overlappingDragDoesNotResumeUntilFinalEnd() {
        LauncherGlassDragSourceState state = new LauncherGlassDragSourceState();

        assertTrue(state.begin());
        assertFalse(state.begin());
        assertFalse(state.end());
        assertTrue(state.isBlocked());
        assertTrue(state.end());
        assertFalse(state.isBlocked());
    }

    @Test
    public void duplicateEndCannotCreateSpuriousResume() {
        LauncherGlassDragSourceState state = new LauncherGlassDragSourceState();

        assertFalse(state.end());
        assertFalse(state.isBlocked());
    }

    @Test
    public void cancelClearsBlockedStateWithoutClaimingFreshResume() {
        LauncherGlassDragSourceState state = new LauncherGlassDragSourceState();
        state.begin();

        state.cancel();

        assertFalse(state.isBlocked());
        assertFalse(state.end());
    }
}
