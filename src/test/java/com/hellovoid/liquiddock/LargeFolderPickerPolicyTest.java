package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class LargeFolderPickerPolicyTest {
    @Test public void sameTypeCompactFoldersRequireExplicitResize() {
        assertTrue(LargeFolderPickerPolicy.mustRequestResize(21, 21, 1, 1));
        assertTrue(LargeFolderPickerPolicy.mustRequestResize(22, 22, 1, 1));
        assertFalse(LargeFolderPickerPolicy.mustRequestResize(21, 21, 2, 2));
        assertFalse(LargeFolderPickerPolicy.mustRequestResize(22, 22, 2, 2));
        assertFalse(LargeFolderPickerPolicy.mustRequestResize(21, 22, 1, 1));
        assertFalse(LargeFolderPickerPolicy.mustRequestResize(2, 21, 1, 1));
    }

    @Test public void disableRevertsBothStylesOnlyWhenSameNativeType() {
        assertTrue(LargeFolderPickerPolicy.mustRestoreCompact(21, 21, 2, 2, true, false));
        assertTrue(LargeFolderPickerPolicy.mustRestoreCompact(22, 22, 2, 2, true, false));
        assertFalse(LargeFolderPickerPolicy.mustRestoreCompact(21, 22, 2, 2, true, false));
        assertFalse(LargeFolderPickerPolicy.mustRestoreCompact(21, 21, 2, 2, true, true));
        assertFalse(LargeFolderPickerPolicy.mustRestoreCompact(22, 22, 1, 1, true, false));
    }
}
