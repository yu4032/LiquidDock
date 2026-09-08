package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VendorDockPassBlurPowerPolicyTest {
    @Test
    public void workspaceSessionOwnsRootUpdateFlag() {
        assertFalse(VendorDockPassBlurPowerPolicy.shouldMirrorDockSnapshot(
                true, false, true));
    }

    @Test
    public void workstationNeverUsesDockSnapshotPowerGate() {
        assertFalse(VendorDockPassBlurPowerPolicy.shouldMirrorDockSnapshot(
                true, true, false));
    }

    @Test
    public void dockOnlyLauncherMayMirrorVendorSnapshotPower() {
        assertTrue(VendorDockPassBlurPowerPolicy.shouldMirrorDockSnapshot(
                true, false, false));
    }

    @Test
    public void disabledGlassDoesNotTouchPassBlurRoot() {
        assertFalse(VendorDockPassBlurPowerPolicy.shouldMirrorDockSnapshot(
                false, false, false));
    }
}
