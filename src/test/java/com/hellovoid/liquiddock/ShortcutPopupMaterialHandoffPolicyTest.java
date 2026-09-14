package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShortcutPopupMaterialHandoffPolicyTest {
    @Test public void keepsVendorMaterialUntilSharedSessionAndSinkAreReady() {
        assertFalse(ShortcutPopupMaterialHandoffPolicy.mayReplaceVendorMaterial(false, false));
        assertFalse(ShortcutPopupMaterialHandoffPolicy.mayReplaceVendorMaterial(true, false));
        assertFalse(ShortcutPopupMaterialHandoffPolicy.mayReplaceVendorMaterial(false, true));
        assertTrue(ShortcutPopupMaterialHandoffPolicy.mayReplaceVendorMaterial(true, true));
    }
}
