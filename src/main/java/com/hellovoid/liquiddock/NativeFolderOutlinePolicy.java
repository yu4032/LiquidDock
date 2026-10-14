package com.hellovoid.liquiddock;

/** Native outline suppression applies only inside a large-folder draw transaction. */
final class NativeFolderOutlinePolicy {
    private NativeFolderOutlinePolicy() {}

    static boolean shouldSuppress(boolean showNativeOutline, boolean drawingLargeFolder) {
        return !showNativeOutline && drawingLargeFolder;
    }
}
