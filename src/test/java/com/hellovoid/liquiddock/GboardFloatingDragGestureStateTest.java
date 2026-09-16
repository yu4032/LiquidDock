package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GboardFloatingDragGestureStateTest {
    @Test public void crossingTouchSlopStartsOnceAndTerminalEndsOnce() {
        GboardFloatingDragGestureState state = new GboardFloatingDragGestureState();
        state.onDown(7, 10f, 20f);

        assertEquals(GboardFloatingDragGestureState.Signal.NONE,
                state.onMove(7, 12f, 22f, 25f));
        assertEquals(GboardFloatingDragGestureState.Signal.STARTED,
                state.onMove(7, 20f, 20f, 25f));
        assertEquals(GboardFloatingDragGestureState.Signal.NONE,
                state.onMove(7, 30f, 20f, 25f));
        assertEquals(GboardFloatingDragGestureState.Signal.ENDED,
                state.onTerminal(7));
        assertEquals(GboardFloatingDragGestureState.Signal.NONE,
                state.onTerminal(7));
    }

    @Test public void tapAndWrongPointerNeverCreateDragLifecycle() {
        GboardFloatingDragGestureState state = new GboardFloatingDragGestureState();
        state.onDown(3, 0f, 0f);

        assertEquals(GboardFloatingDragGestureState.Signal.NONE,
                state.onMove(4, 100f, 100f, 1f));
        assertEquals(GboardFloatingDragGestureState.Signal.NONE,
                state.onTerminal(4));
        assertEquals(GboardFloatingDragGestureState.Signal.NONE,
                state.onTerminal(3));
    }

    @Test public void cancelEndsAnActiveDrag() {
        GboardFloatingDragGestureState state = new GboardFloatingDragGestureState();
        state.onDown(1, 0f, 0f);
        assertEquals(GboardFloatingDragGestureState.Signal.STARTED,
                state.onMove(1, 10f, 0f, 4f));
        assertEquals(GboardFloatingDragGestureState.Signal.ENDED, state.onCancel());
        assertEquals(GboardFloatingDragGestureState.Signal.NONE, state.onCancel());
    }
}
