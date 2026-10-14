package com.hellovoid.liquiddock;

/** Decides whether an explicitly selected fourth folder option needs a native resize. */
final class LargeFolderPickerPolicy {
    private LargeFolderPickerPolicy() {}

    static boolean mustRequestResize(int originalType, int spanX, int spanY) {
        // The stock picker calls convertIconSize(21) when changing from
        // another type. For an existing type-21 / 1x1 folder, the stock
        // confirmation does nothing because itemType has not changed.
        return originalType == 21 && (spanX != 2 || spanY != 2);
    }
}
