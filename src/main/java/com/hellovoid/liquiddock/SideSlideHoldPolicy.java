package com.hellovoid.liquiddock;

/**
 * Android-free eligibility state for the Launcher 4.50 side-slide Sidebar extension.
 *
 * <p>Apps use Launcher's native READY_STATE_RECENT authority. HOME has no Back/RECENT commit
 * state on OS3, so the hook may explicitly provide a HOME-only visual-progress eligibility bit.
 * Both sources converge here and stale acknowledgements remain generation-safe.</p>
 */
final class SideSlideHoldPolicy {
    // OS4 Security Center SidebarTouchListener posts its long-click confirmation at 300 ms.
    static final long HOLD_DWELL_MS = 300L;

    private boolean active;
    private boolean eligible;
    private boolean requestIssued;
    private boolean sidebarAccepted;
    private int generation;

    void onDown() {
        active = true;
        eligible = false;
        requestIssued = false;
        sidebarAccepted = false;
        generation++;
    }

    boolean onReadyState(String stateName) {
        return setEligible("READY_STATE_RECENT".equals(stateName));
    }

    boolean onDesktopProgress(boolean reachedVisualCommit) {
        return setEligible(reachedVisualCommit);
    }

    private boolean setEligible(boolean nextEligible) {
        if (!active || sidebarAccepted) return false;
        if (nextEligible == eligible) return false;
        eligible = nextEligible;
        requestIssued = false;
        generation++;
        return nextEligible;
    }

    int generation() {
        return generation;
    }

    boolean requestSidebar(int expectedGeneration) {
        if (!active || !eligible || requestIssued || sidebarAccepted
                || generation != expectedGeneration) {
            return false;
        }
        requestIssued = true;
        return true;
    }

    void onSidebarResult(boolean accepted, int expectedGeneration) {
        if (!active || !eligible || !requestIssued || generation != expectedGeneration) {
            return;
        }
        sidebarAccepted = accepted;
        if (!accepted) requestIssued = false;
    }

    boolean shouldConsumeVendorCompletion() {
        return active && sidebarAccepted;
    }

    void onUpOrCancel() {
        active = false;
        eligible = false;
        requestIssued = false;
        sidebarAccepted = false;
        generation++;
    }
}
