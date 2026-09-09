package com.hellovoid.liquiddock;

/**
 * Pure scroll state for projecting captured Workspace glass geometry to the latest scroll position.
 * The PassBlur backdrop remains root-anchored; only glass geometry is shifted.
 */
final class LauncherGlassScrollProjectionState {
    private boolean initialized;
    private int initialAnchorScrollX;
    private int currentScrollX;

    synchronized void onScrollMutation(int beforeScrollX, int afterScrollX) {
        if (!initialized) {
            initialAnchorScrollX = beforeScrollX;
            currentScrollX = beforeScrollX;
            initialized = true;
        }
        currentScrollX = afterScrollX;
    }

    synchronized float projectCenterX(
            float capturedCenterX, int capturedScrollX, boolean capturedScrollValid) {
        if (!initialized || !Float.isFinite(capturedCenterX)) return capturedCenterX;
        int anchorScrollX = capturedScrollValid ? capturedScrollX : initialAnchorScrollX;
        return capturedCenterX + (anchorScrollX - currentScrollX);
    }

    synchronized void reset() {
        initialized = false;
        initialAnchorScrollX = 0;
        currentScrollX = 0;
    }
}
