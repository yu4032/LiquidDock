package com.hellovoid.liquiddock;

/** Per-folder title policy; the native multi-span hide is one shared decision. */
final class LargeFolderTitlePolicy {
    private LargeFolderTitlePolicy() {}

    static boolean shouldShow(boolean realFolder, boolean userEnabled, boolean hiddenByModel) {
        return realFolder && userEnabled && !hiddenByModel;
    }

    /**
     * Only override Launcher 4.50's span>1 title suppression.
     * Its no-word and hotseat branches must remain native (GONE).
     */
    static boolean shouldOverrideNativeSpanHide(boolean realFolder, boolean userEnabled,
            boolean hiddenByModel, boolean noWord, boolean inHotseat) {
        return shouldShow(realFolder, userEnabled, hiddenByModel)
                && !noWord && !inHotseat;
    }
}
