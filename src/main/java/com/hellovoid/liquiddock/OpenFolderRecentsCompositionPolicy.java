package com.hellovoid.liquiddock;

/** OPEN_FOLDER output ownership: native Recents owns the screen-wide wallpaper;
 * the frozen PassBlur frame remains only a texture input for the Prismal lens.
 * SHORTCUT_POPUP has its own composition contract and never uses this policy.
 */
final class OpenFolderRecentsCompositionPolicy {
    private OpenFolderRecentsCompositionPolicy() {}

    static boolean drawFrozenWallpaper(boolean expandedFolder, boolean recentsLensOnly) {
        return expandedFolder && !recentsLensOnly;
    }
}
