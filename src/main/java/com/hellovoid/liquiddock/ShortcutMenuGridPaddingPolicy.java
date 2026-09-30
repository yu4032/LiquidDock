package com.hellovoid.liquiddock;

/** Pure policy for correcting ShortcutMenu vertical icon padding under non-square custom grid cells. */
final class ShortcutMenuGridPaddingPolicy {
    private ShortcutMenuGridPaddingPolicy() {}

    static int correctedTopPadding(
            int vendorPadding,
            int actualPaddingTop,
            float workspaceScale,
            boolean activeCustomGridWorkspaceShortcut) {
        if (!activeCustomGridWorkspaceShortcut
                || actualPaddingTop < 0
                || Float.isNaN(workspaceScale)
                || Float.isInfinite(workspaceScale)
                || workspaceScale <= 0f) {
            return vendorPadding;
        }
        return Math.max(0, Math.round(actualPaddingTop * workspaceScale));
    }
}
