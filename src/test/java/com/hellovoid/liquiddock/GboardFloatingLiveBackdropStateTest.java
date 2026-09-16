package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GboardFloatingLiveBackdropStateTest {
    @Test public void releaseKeepsSnapshotUntilFreshBackdropAndSwap() {
        GboardFloatingLiveBackdropState state = new GboardFloatingLiveBackdropState();
        assertTrue(state.onDragStart(true));
        state.onDragEnd(2L);
        assertEquals(GboardFloatingLiveBackdropState.Phase.WAITING_FOR_FRESH_LIVE,
                state.phase());
        assertTrue(state.acceptLiveBackdrop());

        state.onFreshBackdropPrepared(2L);
        assertEquals(GboardFloatingLiveBackdropState.Phase.WAITING_FOR_FRESH_LIVE,
                state.phase());

        state.onFreshOutputSwapped(2L);
        assertEquals(GboardFloatingLiveBackdropState.Phase.LIVE, state.phase());
    }

    @Test public void dragCannotStartWithoutPreparedBackdrop() {
        GboardFloatingLiveBackdropState state = new GboardFloatingLiveBackdropState();
        assertFalse(state.onDragStart(false));
        assertEquals(GboardFloatingLiveBackdropState.Phase.LIVE, state.phase());
    }

    @Test public void staleGenerationCannotReleaseWaitingState() {
        GboardFloatingLiveBackdropState state = new GboardFloatingLiveBackdropState();
        assertTrue(state.onDragStart(true));
        state.onDragEnd(7L);
        state.onFreshBackdropPrepared(6L);
        state.onFreshOutputSwapped(6L);
        assertEquals(GboardFloatingLiveBackdropState.Phase.WAITING_FOR_FRESH_LIVE,
                state.phase());
    }
}
