package com.hellovoid.liquiddock;

/**
 * Keeps the OEM Dock FolderState count balanced when the native open event is
 * dispatched before Launcher reaches its normal late dispatch site.
 */
final class FolderDockOpenDispatch {
    private boolean awaitingNativeOpen;

    boolean hasPendingNativeOpen() { return awaitingNativeOpen; }

    void onEarlyDispatched() { awaitingNativeOpen = true; }

    /** True only for the late duplicate of our already-forwarded native event. */
    boolean consumeLateNativeOpen() {
        if (!awaitingNativeOpen) return false;
        awaitingNativeOpen = false;
        return true;
    }

    void onClose() { awaitingNativeOpen = false; }
}
