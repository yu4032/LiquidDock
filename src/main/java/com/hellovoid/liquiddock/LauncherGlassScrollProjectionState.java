package com.hellovoid.liquiddock;

/**
 * Scroll projection state. One GL frame uses one immutable snapshot; it must not
 * combine different Workspace scroll positions across its glass nodes.
 */
final class LauncherGlassScrollProjectionState {
    static final class Snapshot {
        final long revision;
        final boolean initialized;
        final int initialAnchorScrollX;
        final int currentScrollX;

        Snapshot(long revision, boolean initialized, int initialAnchorScrollX,
                int currentScrollX) {
            this.revision = revision;
            this.initialized = initialized;
            this.initialAnchorScrollX = initialAnchorScrollX;
            this.currentScrollX = currentScrollX;
        }

        float projectCenterX(
                float capturedCenterX, int capturedScrollX, boolean capturedScrollValid) {
            if (!initialized || !Float.isFinite(capturedCenterX)) return capturedCenterX;
            int anchor = capturedScrollValid ? capturedScrollX : initialAnchorScrollX;
            return capturedCenterX + (anchor - currentScrollX);
        }
    }

    private boolean initialized;
    private int initialAnchorScrollX;
    private int currentScrollX;
    private long revision;

    synchronized void onScrollMutation(int beforeScrollX, int afterScrollX) {
        if (beforeScrollX == afterScrollX) return;
        if (!initialized) {
            initialAnchorScrollX = beforeScrollX;
            currentScrollX = beforeScrollX;
            initialized = true;
        }
        currentScrollX = afterScrollX;
        revision++;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(revision, initialized, initialAnchorScrollX, currentScrollX);
    }

    synchronized boolean isCurrent(Snapshot snapshot) {
        return snapshot != null && snapshot.revision == revision;
    }

    synchronized void reset() {
        initialized = false;
        initialAnchorScrollX = 0;
        currentScrollX = 0;
        revision++;
    }
}
