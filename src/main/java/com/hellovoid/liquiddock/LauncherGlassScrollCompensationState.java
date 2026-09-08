package com.hellovoid.liquiddock;

/** Pure late-latch state for aligning a rendered static glass frame with live Workspace scroll. */
final class LauncherGlassScrollCompensationState {
    private boolean initialized;
    private int currentScrollX;
    private int renderedScrollX;

    float onScrollMutation(int beforeScrollX, int afterScrollX) {
        if (!initialized) {
            renderedScrollX = beforeScrollX;
            currentScrollX = beforeScrollX;
            initialized = true;
        }
        currentScrollX = afterScrollX;
        return renderedScrollX - currentScrollX;
    }

    float onFramePresented(int frameScrollX) {
        if (!initialized) {
            renderedScrollX = frameScrollX;
            currentScrollX = frameScrollX;
            initialized = true;
            return 0f;
        }
        renderedScrollX = frameScrollX;
        return renderedScrollX - currentScrollX;
    }

    void reset() {
        initialized = false;
        currentScrollX = 0;
        renderedScrollX = 0;
    }
}
