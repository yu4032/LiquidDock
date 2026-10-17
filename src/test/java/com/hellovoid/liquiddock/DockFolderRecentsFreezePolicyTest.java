package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Dock/native Recents owns presentation separately from FolderCling lifecycle. */
public class DockFolderRecentsFreezePolicyTest {
    @Test public void folderToRecentsAndBackNeverStrandsDockFrozenInRecents() {
        boolean folderOpen = true;
        boolean recentsVisible = false;
        assertTrue(DockFolderRecentsFreezePolicy.shouldFreeze(folderOpen, recentsVisible));

        // Launcher can expose the Dock in Recents without sending Folder.close.
        recentsVisible = true;
        assertFalse(DockFolderRecentsFreezePolicy.shouldFreeze(folderOpen, recentsVisible));

        // Returning to the still-open folder must retain its native modal gate.
        recentsVisible = false;
        assertTrue(DockFolderRecentsFreezePolicy.shouldFreeze(folderOpen, recentsVisible));

        // Only native CLOSE/abort can drop folder ownership.
        folderOpen = false;
        assertFalse(DockFolderRecentsFreezePolicy.shouldFreeze(folderOpen, recentsVisible));
    }

    @Test public void dockRemainsLiveForOrdinaryRecentsWithoutFolder() {
        assertFalse(DockFolderRecentsFreezePolicy.shouldFreeze(false, false));
        assertFalse(DockFolderRecentsFreezePolicy.shouldFreeze(false, true));
    }

    @Test public void closingFolderUnderRecentsDoesNotRefreezeOnReturn() {
        boolean recentsVisible = true;
        assertFalse(DockFolderRecentsFreezePolicy.shouldFreeze(true, recentsVisible));
        assertFalse(DockFolderRecentsFreezePolicy.shouldFreeze(false, recentsVisible));
        recentsVisible = false;
        assertFalse(DockFolderRecentsFreezePolicy.shouldFreeze(false, recentsVisible));
    }
}
