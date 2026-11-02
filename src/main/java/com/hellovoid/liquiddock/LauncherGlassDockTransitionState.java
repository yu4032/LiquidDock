package com.hellovoid.liquiddock;

/**
 * Cross-window presentation ownership for Dock shortcut launch/return.
 *
 * Dock ShortcutIcon is commonly hosted in a separate ViewRoot, while Workspace's
 * shared glass output is a Launcher-root sibling. This is only an animation owner,
 * not a source/freshness or popup-capture barrier.
 */
final class LauncherGlassDockTransitionState {
    static final class Sample {
        final boolean active;
        final boolean returning;
        final float progress;
        Sample(boolean active, boolean returning, float progress) {
            this.active = active;
            this.returning = returning;
            this.progress = progress;
        }
    }

    static Sample mergeWorkspace(boolean workspaceActive, boolean workspaceReturning,
            float workspaceProgress, Sample dock) {
        if (dock == null) return new Sample(workspaceActive, workspaceReturning,
                workspaceProgress);
        boolean mergedActive = workspaceActive || dock.active;
        boolean mergedReturning = workspaceReturning || (dock.active && dock.returning);
        float mergedProgress = workspaceReturning
                ? Math.max(workspaceProgress, dock.returning ? dock.progress : 0f)
                : dock.returning ? dock.progress : workspaceProgress;
        return new Sample(mergedActive, mergedActive && mergedReturning, mergedProgress);
    }

    private boolean active;
    private boolean returning;
    private boolean homeInProgress;
    private boolean sourceRestored;
    private float progress;

    synchronized boolean onNativeHidden() {
        // A repeated OEM hide must not restart a HOME return or its reveal.
        if (active) return false;
        active = true;
        returning = false;
        sourceRestored = false;
        progress = 0f;
        return true;
    }

    synchronized boolean onNativeVisible() {
        if (returning && homeInProgress) return false;
        // Initial or repeated VISIBLE callbacks do not retire a proxy that hasn't begun.
        if (!active) return false;
        active = false;
        returning = false;
        progress = 0f;
        sourceRestored = true;
        return changed;
    }

    synchronized boolean onHomeStarted() {
        homeInProgress = true;
        if (!active || returning) return false;
        returning = true;
        progress = 0f;
        return true;
    }

    synchronized boolean onProxyFrame(boolean closeToHome, float nativeProgress) {
        // Ignore late frame callbacks from a previously completed proxy. A Dock target
        // may still enter a new HOME proxy without a corresponding hide callback.
        if (sourceRestored && !homeInProgress) return false;
        // Some Launcher builds publish a FloatingIcon update before the source hide callback.
        // Let the first proxy establish ownership unless the prior cycle is terminal.
        if (!active) {
            active = true;
            sourceRestored = false;
        }
        boolean changed = returning != closeToHome;
        returning = closeToHome;
        float next = Float.isFinite(nativeProgress)
                ? Math.max(0f, Math.min(1f, nativeProgress)) : progress;
        if (Float.compare(progress, next) != 0) changed = true;
        progress = next;
        return changed;
    }

    synchronized boolean onHomeProgress(float nativeProgress) {
        if (!active || !homeInProgress || !Float.isFinite(nativeProgress)) return false;
        boolean changed = !returning;
        returning = true;
        float next = Math.max(0f, Math.min(1f, nativeProgress));
        if (Float.compare(progress, next) != 0) changed = true;
        progress = next;
        return changed;
    }

    /**
     * The Dock FloatingIcon handoff is its own authoritative visual terminal, even when
     * WindowElement completion is delayed or absent. Workspace's existing fade will keep
     * rendering after this proxy stops owning the launch presentation.
     */
    synchronized boolean onProxyReturnFinished() {
        if (!active || !returning) return false;
        active = false;
        returning = false;
        homeInProgress = false;
        sourceRestored = true;
        progress = 0f;
        return true;
    }

    synchronized boolean onHomeRetargetedAway() {
        homeInProgress = false;
        if (!active) return false;
        boolean changed = returning;
        returning = false;
        progress = 0f;
        return changed;
    }

    synchronized boolean onHomeSettled() {
        boolean changed = active || homeInProgress || returning;
        active = false;
        homeInProgress = false;
        returning = false;
        sourceRestored = true;
        progress = 0f;
        return changed;
    }

    synchronized Sample sample() {
        return new Sample(active, returning, progress);
    }
}
