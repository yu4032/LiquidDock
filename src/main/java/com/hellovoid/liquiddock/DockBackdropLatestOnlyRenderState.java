package com.hellovoid.liquiddock;

/**
 * Latest-only render scheduler state for the Dock backdrop.
 *
 * At most one render may be queued or running. Requests that arrive while that render is active
 * are collapsed into one follow-up render so obsolete intermediate geometry never builds a FIFO
 * backlog ahead of the newest Dock position/producer frame.
 */
final class DockBackdropLatestOnlyRenderState {
    static final class Decision {
        final boolean postRender;

        Decision(boolean postRender) {
            this.postRender = postRender;
        }
    }

    private boolean queuedOrRunning;
    private boolean dirty;

    synchronized Decision requestRender() {
        dirty = true;
        if (queuedOrRunning) return new Decision(false);
        queuedOrRunning = true;
        return new Decision(true);
    }

    synchronized void beginRender() {
        dirty = false;
    }

    synchronized Decision finishRender() {
        if (dirty) {
            // Keep ownership while one follow-up run is queued. All additional requests collapse
            // into that run until beginRender() clears dirty again.
            return new Decision(true);
        }
        queuedOrRunning = false;
        return new Decision(false);
    }

    synchronized void reset() {
        queuedOrRunning = false;
        dirty = false;
    }
}
