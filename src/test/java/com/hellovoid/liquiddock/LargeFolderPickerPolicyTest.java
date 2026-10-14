package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Checks when a fourth folder-style selection must request a native resize. */
public class LargeFolderPickerPolicyTest {
    @Test public void sameTypeCompactFolderRequiresResize() {
        assertTrue(LargeFolderPickerPolicy.mustRequestResize(21, 1, 1));
        assertTrue(LargeFolderPickerPolicy.mustRequestResize(21, 1, 2));
        assertFalse(LargeFolderPickerPolicy.mustRequestResize(21, 2, 2));
    }

    @Test public void aChangedFolderTypeIsAlreadyHandledByStockConfirmation() {
        assertFalse(LargeFolderPickerPolicy.mustRequestResize(2, 1, 1));
        assertFalse(LargeFolderPickerPolicy.mustRequestResize(22, 2, 2));
    }
}
