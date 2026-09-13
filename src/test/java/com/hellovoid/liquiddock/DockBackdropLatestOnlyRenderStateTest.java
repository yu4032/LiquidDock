package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Render scheduling must drop obsolete intermediate Dock backdrop work instead of queueing it. */
public class DockBackdropLatestOnlyRenderStateTest {
    @Test
    public void burstBeforeRenderPostsOnlyOneDraw() {
        DockBackdropLatestOnlyRenderState state = new DockBackdropLatestOnlyRenderState();

        assertTrue(state.requestRender().postRender);
        assertFalse(state.requestRender().postRender);
        assertFalse(state.requestRender().postRender);

        state.beginRender();
        assertFalse(state.finishRender().postRender);
    }

    @Test
    public void requestDuringRenderProducesExactlyOneFollowup() {
        DockBackdropLatestOnlyRenderState state = new DockBackdropLatestOnlyRenderState();
        assertTrue(state.requestRender().postRender);

        state.beginRender();
        assertFalse(state.requestRender().postRender);
        assertFalse(state.requestRender().postRender);
        assertTrue(state.finishRender().postRender);

        state.beginRender();
        assertFalse(state.finishRender().postRender);
    }

    @Test
    public void idleAfterCompletionCanScheduleAgain() {
        DockBackdropLatestOnlyRenderState state = new DockBackdropLatestOnlyRenderState();
        assertTrue(state.requestRender().postRender);
        state.beginRender();
        assertFalse(state.finishRender().postRender);

        assertTrue(state.requestRender().postRender);
    }
}
