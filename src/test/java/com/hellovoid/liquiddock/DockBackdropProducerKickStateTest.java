package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DockBackdropProducerKickStateTest {
    @Test
    public void firstGeometryChangePostsKick() {
        DockBackdropProducerKickState state = new DockBackdropProducerKickState();

        DockBackdropProducerKickState.Decision decision = state.onGeometryChanged();

        assertTrue(decision.postKick);
    }

    @Test
    public void repeatedChangesCoalesceUntilKickRuns() {
        DockBackdropProducerKickState state = new DockBackdropProducerKickState();

        assertTrue(state.onGeometryChanged().postKick);
        assertFalse(state.onGeometryChanged().postKick);
        assertFalse(state.onGeometryChanged().postKick);
    }

    @Test
    public void completedKickAllowsNextGeometryChangeToPostAgain() {
        DockBackdropProducerKickState state = new DockBackdropProducerKickState();

        assertTrue(state.onGeometryChanged().postKick);
        state.onKickConsumed();

        assertTrue(state.onGeometryChanged().postKick);
    }

    @Test
    public void resetClearsPendingKick() {
        DockBackdropProducerKickState state = new DockBackdropProducerKickState();

        assertTrue(state.onGeometryChanged().postKick);
        state.reset();

        assertTrue(state.onGeometryChanged().postKick);
    }
}
