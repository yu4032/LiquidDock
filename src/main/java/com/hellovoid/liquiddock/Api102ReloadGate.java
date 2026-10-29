package com.hellovoid.liquiddock;

/**
 * Process-local generation handoff gate.
 *
 * <p>An install attempt irrevocably claims ownership for this module generation. A successful
 * pre-install handoff closes the old generation against later package-ready callbacks. This does
 * not release hooks, listeners, receivers or native graphics resources, and is not evidence that
 * automatic APK hot reload is safe.</p>
 */
final class Api102ReloadGate {
    private enum State { PRE_INSTALL, OWNED, HANDOFF }

    private State state = State.PRE_INSTALL;

    /** Must run before any target process bootstrap, even if that bootstrap might fail. */
    synchronized boolean enterOwnerLifecycle() {
        if (state == State.HANDOFF) return false;
        state = State.OWNED;
        return true;
    }

    /** Admit handoff only before the first owner attempted initialization. */
    synchronized boolean beginHandoff() {
        if (state != State.PRE_INSTALL) return false;
        state = State.HANDOFF;
        return true;
    }

    /**
     * For a process whose every owner has already been retired. The caller must prove
     * no owned hooks, external listeners, native sessions or live Views remain.
     */
    synchronized boolean beginRetiredOwnerHandoff() {
        if (state != State.OWNED) return false;
        state = State.HANDOFF;
        return true;
    }

    /** Roll back a failed saved-state transfer without admitting a concurrent install. */
    synchronized void abortHandoff() {
        if (state == State.HANDOFF) state = State.PRE_INSTALL;
    }
}
