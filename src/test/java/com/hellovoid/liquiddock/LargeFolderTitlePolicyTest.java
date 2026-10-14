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

    @Test public void onlyOverrideNativeSpanInvisibleFromBothMeasureAndDraw() {
        assertTrue(LargeFolderTitlePolicy.shouldRestoreAfterNativeVisibility(
                true, true, false, 4));
        assertFalse(LargeFolderTitlePolicy.shouldRestoreAfterNativeVisibility(
                true, true, false, 8)); // No-word mode is a GONE decision.
        assertFalse(LargeFolderTitlePolicy.shouldRestoreAfterNativeVisibility(
                true, true, false, 0));
        assertFalse(LargeFolderTitlePolicy.shouldRestoreAfterNativeVisibility(
                true, false, false, 4));
        assertFalse(LargeFolderTitlePolicy.shouldRestoreAfterNativeVisibility(
                true, true, true, 4));
        assertFalse(LargeFolderTitlePolicy.shouldRestoreAfterNativeVisibility(
                false, true, false, 4));
    }
}
