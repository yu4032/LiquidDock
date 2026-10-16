package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class FolderPreviewClosePolicyTest {
    @Test public void onlyVisibleSourcePreviewMayBeRestored() {
        assertFalse(FolderPreviewClosePolicy.shouldRestore(false, true, 0f));
        assertFalse(FolderPreviewClosePolicy.shouldRestore(true, false, 0f));
        assertTrue(FolderPreviewClosePolicy.shouldRestore(true, true, 0f));
        assertTrue(FolderPreviewClosePolicy.shouldRestore(true, true, .35f));
        assertFalse(FolderPreviewClosePolicy.shouldRestore(true, true, 1f));
        assertFalse(FolderPreviewClosePolicy.shouldRestore(true, true, Float.NaN));
    }

    @Test public void workspaceItselfKeepsAuthorityOverNativeReveal() {
        // When Workspace is hidden by the OEM, a child's local opacity must
        // not make the source preview visible or alter native reverse spring.
        assertFalse(FolderPreviewClosePolicy.shouldRestore(false, true, .1f));
        assertFalse(FolderPreviewClosePolicy.shouldRestore(false, true, .9f));
    }
}
