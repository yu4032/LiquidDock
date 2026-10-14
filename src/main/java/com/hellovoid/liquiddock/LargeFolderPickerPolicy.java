package com.hellovoid.liquiddock;

/** One real 2x2 switch shared by both native large-folder styles. */
final class LargeFolderPickerPolicy {
    private LargeFolderPickerPolicy() {}

    static boolean mustRequestResize(int originalType, int targetType, int spanX, int spanY) {
        // A change of native type is converted by FolderSheet itself.
        // Same-type conversions must be explicitly requested if the span differs.
        return LargeFolderFootprintPolicy.supportsType(targetType)
                && originalType == targetType && (spanX != 2 || spanY != 2);
    }

    static boolean mustRestoreCompact(int originalType, int targetType,
            int spanX, int spanY, boolean wasCustom, boolean nowCustom) {
        return wasCustom && !nowCustom && originalType == targetType
                && LargeFolderFootprintPolicy.supportsType(targetType)
                && spanX == 2 && spanY == 2;
    }
}
