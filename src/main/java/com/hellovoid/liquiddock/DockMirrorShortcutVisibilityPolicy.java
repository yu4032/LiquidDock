package com.hellovoid.liquiddock;

/** Pure policy for hiding the Dock mirror presentation while preserving the surviving icon anchor. */
final class DockMirrorShortcutVisibilityPolicy {
    static final int NO_VIEW_TYPE = -1;
    static final int DIVIDER_VIEW_TYPE = 64;
    static final int MIRROR_VIEW_TYPE = 4096;

    private DockMirrorShortcutVisibilityPolicy() {}

    static boolean needsVisibilityOwnership(int viewType) {
        return viewType == MIRROR_VIEW_TYPE || viewType == DIVIDER_VIEW_TYPE;
    }

    static boolean shouldHideItem(
            boolean hideMirrorShortcut,
            int position,
            int itemCount,
            int viewType,
            int trailingViewType) {
        if (!hideMirrorShortcut) return false;
        if (viewType == MIRROR_VIEW_TYPE) return true;

        // Vendor trailing-side mirror composition is [divider, mirror]. Collapse that dedicated
        // divider with the mirror, but never touch unrelated dividers elsewhere in the Dock.
        return viewType == DIVIDER_VIEW_TYPE
                && itemCount >= 2
                && position == itemCount - 2
                && trailingViewType == MIRROR_VIEW_TYPE;
    }

    static int compensationDirection(int mirrorPosition, int itemCount, boolean rtl) {
        if (itemCount <= 0 || mirrorPosition < 0 || mirrorPosition >= itemCount) return 0;

        // Removing a leading item pulls the remaining centered row toward physical start, so move
        // it back toward end. Removing a trailing item does the opposite. Flexbox ROW reverses
        // physical start/end under RTL.
        boolean logicalLeading = mirrorPosition < itemCount / 2;
        int ltrDirection = logicalLeading ? 1 : -1;
        return rtl ? -ltrDirection : ltrDirection;
    }
}
