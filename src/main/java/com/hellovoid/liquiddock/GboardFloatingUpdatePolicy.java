package com.hellovoid.liquiddock;

/** Typed policy for deciding Gboard floating-glass producer and render work. */
final class GboardFloatingUpdatePolicy {
    enum Cause {
        INITIAL_CAPTURE,
        GEOMETRY,
        FRESH_FRAME,
        OUTPUT_RESIZE
    }

    private GboardFloatingUpdatePolicy() {}

    static boolean shouldReconcileRoot(Cause cause) {
        return cause == Cause.INITIAL_CAPTURE;
    }

    static boolean shouldRequestRender(Cause cause) {
        return cause == Cause.GEOMETRY
                || cause == Cause.FRESH_FRAME
                || cause == Cause.OUTPUT_RESIZE;
    }
}
