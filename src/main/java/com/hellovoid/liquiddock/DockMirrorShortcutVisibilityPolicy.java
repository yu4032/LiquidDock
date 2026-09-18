package com.hellovoid.liquiddock;

/** Pure policy for hiding the Dock mirror presentation while preserving its layout footprint. */
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

        // In the vendor list, a mirror shortcut placed on the trailing side is appended as
        // [divider, mirror]. Hide that divider's pixels as well, but leave both measured items
        // present so CENTER justification and custom Dock spacing keep exactly the same geometry.
        return viewType == DIVIDER_VIEW_TYPE
                && itemCount >= 2
                && position == itemCount - 2
                && trailingViewType == MIRROR_VIEW_TYPE;
    }
}
