package com.hellovoid.liquiddock;

/** Android-free active-pointer drag lifecycle used by the floating handle wrapper. */
final class GboardFloatingDragGestureState {
    enum Signal { NONE, STARTED, ENDED }

    private int activePointerId = -1;
    private float downX;
    private float downY;
    private boolean dragged;

    void onDown(int pointerId, float x, float y) {
        activePointerId = pointerId;
        downX = x;
        downY = y;
        dragged = false;
    }

    Signal onMove(int pointerId, float x, float y, float touchSlopSquared) {
        if (activePointerId < 0 || pointerId != activePointerId || dragged) return Signal.NONE;
        float dx = x - downX;
        float dy = y - downY;
        if ((dx * dx) + (dy * dy) <= touchSlopSquared) return Signal.NONE;
        dragged = true;
        return Signal.STARTED;
    }

    Signal onTerminal(int pointerId) {
        if (activePointerId < 0 || pointerId != activePointerId) return Signal.NONE;
        Signal signal = dragged ? Signal.ENDED : Signal.NONE;
        reset();
        return signal;
    }

    Signal onCancel() {
        if (activePointerId < 0) return Signal.NONE;
        Signal signal = dragged ? Signal.ENDED : Signal.NONE;
        reset();
        return signal;
    }

    private void reset() {
        activePointerId = -1;
        dragged = false;
    }
}
