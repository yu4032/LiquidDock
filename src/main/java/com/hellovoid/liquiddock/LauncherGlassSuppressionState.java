package com.hellovoid.liquiddock;

/** Android-free ownership state for independent folder-open and drag suppression claims. */
final class LauncherGlassSuppressionState {
    private boolean folderOpen;
    private boolean drag;

    boolean setFolderOpen(boolean suppressed) {
        if (folderOpen == suppressed) return false;
        folderOpen = suppressed;
        return true;
    }

    boolean setDrag(boolean suppressed) {
        if (drag == suppressed) return false;
        drag = suppressed;
        return true;
    }

    boolean isFolderOpenSuppressed() { return folderOpen; }
    boolean isDragSuppressed() { return drag; }
    boolean isSuppressed() { return folderOpen || drag; }

    /** Reattach must preserve every still-active suppression owner instead of forcing visible. */
    boolean shouldShowOnAttach() { return !isSuppressed(); }
}
