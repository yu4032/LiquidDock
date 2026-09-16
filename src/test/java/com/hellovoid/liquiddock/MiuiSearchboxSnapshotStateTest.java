package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MiuiSearchboxSnapshotStateTest {
    @Test
    public void oneFreshFrameIsAcceptedPerVisibleCaptureCycle() {
        MiuiSearchboxSnapshotState state = new MiuiSearchboxSnapshotState();

        state.beginCapture();
        assertTrue(state.acceptFreshFrame());
        assertFalse(state.acceptFreshFrame());
        assertFalse(state.acceptFreshFrame());

        state.beginCapture();
        assertTrue(state.acceptFreshFrame());
        assertFalse(state.acceptFreshFrame());
    }
}
