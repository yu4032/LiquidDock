package com.hellovoid.liquiddock;

/**
 * Tracks the native caption fade-out transition without relying on an R8 method name.
 * An early detach request never authorizes producer release: the compositor commit does.
 */
final class HandleMenuEarlyDetachPolicy {
    private boolean seenOpen;
    private boolean closeStarted;

    synchronized boolean shouldBeginDetach(float alpha, boolean prismalPresented) {
        if (!Float.isFinite(alpha) || closeStarted) return false;
        if (alpha >= 0.98f) seenOpen = true;
        if (!seenOpen || !prismalPresented || alpha > 0.85f) return false;
        closeStarted = true;
        return true;
    }
}
