package com.hellovoid.liquiddock;

/** Dock output belongs to its own native surface.
 * A modal folder blocks Dock glass rendering while HOME owns the screen,
 * but native Recents can expose Dock again without closing FolderCling.
 * Recents coverage temporarily takes priority over the folder draw gate;
 * it never releases the folder's actual lifecycle or frozen optical source.
 */
final class DockFolderRecentsFreezePolicy {
    private DockFolderRecentsFreezePolicy() {}

    static boolean shouldFreeze(boolean folderOpen, boolean recentsVisible) {
        return folderOpen && !recentsVisible;
    }
}
