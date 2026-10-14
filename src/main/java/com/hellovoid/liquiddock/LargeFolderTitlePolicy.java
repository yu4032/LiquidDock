package com.hellovoid.liquiddock;

/** Per-folder title policy, independent of transient drag/view state. */
final class LargeFolderTitlePolicy {
    private LargeFolderTitlePolicy() {}

    static boolean shouldShow(boolean realFolder, boolean userEnabled, boolean hiddenByModel) {
        return realFolder && userEnabled && !hiddenByModel;
    }

    /**
     * OEM native measurement hides every multi-cell ItemIcon via INVISIBLE=4.
     * Do not override GONE=8: it is the platform no-word/hotseat policy.
     */
    static boolean shouldRestoreAfterNativeMeasure(boolean realFolder, boolean userEnabled,
            boolean hiddenByModel, int nativeVisibility) {
        return nativeVisibility == 4 && shouldShow(realFolder, userEnabled, hiddenByModel);
    }
}
