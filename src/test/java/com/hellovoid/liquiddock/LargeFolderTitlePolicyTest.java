package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class LargeFolderTitlePolicyTest {
    @Test public void showOnlyWhenCustomEnabledAndAllowedByModel() {
        assertTrue(LargeFolderTitlePolicy.shouldShow(true, true, false));
        assertFalse(LargeFolderTitlePolicy.shouldShow(true, false, false));
        assertFalse(LargeFolderTitlePolicy.shouldShow(true, true, true));
        assertFalse(LargeFolderTitlePolicy.shouldShow(false, true, false));
    }
}
