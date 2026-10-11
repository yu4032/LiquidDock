package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class LauncherGlassOutputRenderStateTest {
    @Test
    public void sourceRenderingConsumesAlreadyQueuedGeometryRedraw() {
        LauncherGlassOutputRenderState state = new LauncherGlassOutputRenderState();
        assertTrue(state.request(true, false));
        assertFalse(state.request(false, true));

        state.consumeForSourceRender();

        assertEquals(0, state.consumeQueuedRender());
        assertFalse(state.finishQueuedRender());
        assertTrue(state.request(true, false));
    }

    @Test
    public void changesArrivingDuringSourceRenderingAreStillRendered() {
        LauncherGlassOutputRenderState state = new LauncherGlassOutputRenderState();
        state.request(true, true);
        state.consumeForSourceRender();
        assertFalse(state.request(false, true));

        assertEquals(LauncherGlassOutputRenderState.DRAG, state.consumeQueuedRender());
        assertFalse(state.finishQueuedRender());
    }

    @Test
    public void changesDuringGeometryDrawRepostOnceAndKeepBothOutputs() {
        LauncherGlassOutputRenderState state = new LauncherGlassOutputRenderState();
        assertTrue(state.request(true, false));
        assertEquals(LauncherGlassOutputRenderState.STATIC, state.consumeQueuedRender());
        assertFalse(state.request(false, true));
        assertFalse(state.request(true, false));
        assertTrue(state.finishQueuedRender());
        assertEquals(3, state.consumeQueuedRender());
        assertFalse(state.finishQueuedRender());
    }

    @Test
    public void rejectedPostRetainsDirtyWorkForNextRequest() {
        LauncherGlassOutputRenderState state = new LauncherGlassOutputRenderState();
        state.request(true, false);
        state.onPostRejected();

        assertTrue(state.request(false, true));
        assertEquals(3, state.consumeQueuedRender());
        assertFalse(state.finishQueuedRender());
    }

    @Test
    public void sourceRenderingWithNoQueuedWorkDoesNotSuppressNextGeometryRequest() {
        LauncherGlassOutputRenderState state = new LauncherGlassOutputRenderState();
        state.consumeForSourceRender();

        assertTrue(state.request(true, true));
        assertEquals(3, state.consumeQueuedRender());
        assertFalse(state.finishQueuedRender());
    }
}
