package com.hellovoid.liquiddock;

/** Title visibility belongs to the selected real-folder style, not transient drag state. */
final class LargeFolderTitlePolicy {
    private LargeFolderTitlePolicy() {}

    static boolean shouldShow(boolean realFolder, boolean userEnabled, boolean hiddenByModel) {
        return realFolder && userEnabled && !hiddenByModel;
    }
}
