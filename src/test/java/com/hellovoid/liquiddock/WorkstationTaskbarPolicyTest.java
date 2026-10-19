package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class WorkstationTaskbarPolicyTest {
    @Test public void onlyActiveWorkstationWithAttachedPanelReplacesDockBody() {
        assertTrue(WorkstationTaskbarPolicy.shouldReplace(true, true, true));
        assertFalse(WorkstationTaskbarPolicy.shouldReplace(false, true, true));
        assertFalse(WorkstationTaskbarPolicy.shouldReplace(true, false, true));
        assertFalse(WorkstationTaskbarPolicy.shouldReplace(true, true, false));
    }

    @Test public void replacementCoversNativeBackgroundWhenLiquidGlassIsOff() {
        assertTrue(WorkstationTaskbarPolicy.shouldHideVendorMaterial(
                true, true, true, false));
        assertFalse(WorkstationTaskbarPolicy.shouldHideVendorMaterial(
                true, true, true, true));
        assertFalse(WorkstationTaskbarPolicy.shouldHideVendorMaterial(
                false, true, true, false));
        assertFalse(WorkstationTaskbarPolicy.shouldHideVendorMaterial(
                true, false, true, false));
        assertFalse(WorkstationTaskbarPolicy.shouldHideVendorMaterial(
                true, true, false, false));
    }
}
