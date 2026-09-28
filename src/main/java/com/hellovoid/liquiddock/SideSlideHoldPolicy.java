package com.hellovoid.liquiddock;

/**
 * Android-free state for extending Launcher's native RECENT-ready edge gesture into Sidebar.
 *
 * <p>Distance, direction and stability are intentionally not reproduced here. Launcher 4.50's
 * GestureStubView already owns those decisions and publishes them through ReadyState.</p>
 */
final class SideSlideHoldPolicy {
    static final long HOLD_DWELL_MS = 600L;

    private boolean active;
    private boolean recentReady;
    private boolean requestIssued;
    private boolean sidebarAccepted;
    private int generation;

    void onDown() {
        active = true;
        recentReady = false;
        requestIssued = false;
        sidebarAccepted = false;
        generation++;
    }

    boolean onReadyState(String stateName) {
        if (!active || sidebarAccepted) return false;
        boolean nextRecent = "READY_STATE_RECENT".equals(stateName);
        if (nextRecent == recentReady) return false;

        recentReady = nextRecent;
        requestIssued = false;
        generation++;
        return nextRecent;
    }

    int generation() {
        return generation;
    }

    boolean requestSidebar(int expectedGeneration) {
        if (!active || !recentReady || requestIssued || sidebarAccepted
                || generation != expectedGeneration) {
            return false;
        }
        requestIssued = true;
        return true;
    }

    void onSidebarResult(boolean accepted, int expectedGeneration) {
        if (!active || !recentReady || !requestIssued || generation != expectedGeneration) {
            return;
        }
        sidebarAccepted = accepted;
        if (!accepted) {
            requestIssued = false;
        }
    }

    boolean shouldConsumeVendorCompletion() {
        return active && sidebarAccepted;
    }

    void onUpOrCancel() {
        active = false;
        recentReady = false;
        requestIssued = false;
        sidebarAccepted = false;
        generation++;
    }
}
