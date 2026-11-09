package com.hellovoid.liquiddock;

/** Ownership checks for a ShortcutMenu opened from the non-Home Floating Dock. */
final class ShortcutPopupDockHandoffPolicy {
    private ShortcutPopupDockHandoffPolicy() {}

    /**
     * A Dock touch can create its capture in a different ViewRoot from ShortcutMenuLayer
     * or the menu decor. Never require Java view-class identity across those windows.
     */
    static boolean matchesDisplay(boolean dockEarly, int sourceDisplayId, int candidateDisplayId) {
        return dockEarly && sourceDisplayId >= 0 && candidateDisplayId >= 0
                && sourceDisplayId == candidateDisplayId;
    }

    /** Direct Dock menu routes may skip ShortcutMenuLayer.setRequestingItemInfo(). */
    static boolean canAdoptOnShow(boolean dockEarly, boolean requestStarted,
                                  boolean sourceAlive, boolean popupAlreadyBound) {
        return dockEarly && !requestStarted && sourceAlive && !popupAlreadyBound;
    }
}
