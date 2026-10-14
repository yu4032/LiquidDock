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

    @Test public void overrideOnlyNativeSpanSuppressionForEnabledRealFolder() {
        assertTrue(LargeFolderTitlePolicy.shouldOverrideNativeSpanHide(
                true, true, false, false, false));
        assertFalse(LargeFolderTitlePolicy.shouldOverrideNativeSpanHide(
                false, true, false, false, false));
        assertFalse(LargeFolderTitlePolicy.shouldOverrideNativeSpanHide(
                true, false, false, false, false));
        assertFalse(LargeFolderTitlePolicy.shouldOverrideNativeSpanHide(
                true, true, true, false, false));
        assertFalse(LargeFolderTitlePolicy.shouldOverrideNativeSpanHide(
                true, true, false, true, false));
        assertFalse(LargeFolderTitlePolicy.shouldOverrideNativeSpanHide(
                true, true, false, false, true));
    }
}
