package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherVendorBlurWritePolicyTest {
    @Test public void unownedVendorWritesAlwaysPassThrough() {
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressPassWindowWrite(false, true));
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressPassWindowWrite(false, false));
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressBlurModeWrite(false, 0));
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressBlurModeWrite(false, 1));
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressBlurRadiusWrite(false, 0));
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressBlurRadiusWrite(false, 100));
    }

    @Test public void liquidDockOwnerKeepsGateAndModesAuthoritative() {
        assertTrue(LauncherVendorBlurWritePolicy.shouldSuppressPassWindowWrite(true, true));
        assertTrue(LauncherVendorBlurWritePolicy.shouldSuppressPassWindowWrite(true, false));
        assertTrue(LauncherVendorBlurWritePolicy.shouldSuppressBlurModeWrite(true, 0));
        assertTrue(LauncherVendorBlurWritePolicy.shouldSuppressBlurModeWrite(true, 1));
    }

    @Test public void zeroRadiusMayPassButVisibleVendorRadiusIsSuppressed() {
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressBlurRadiusWrite(true, 0));
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressBlurRadiusWrite(true, -1));
        assertTrue(LauncherVendorBlurWritePolicy.shouldSuppressBlurRadiusWrite(true, 1));
        assertTrue(LauncherVendorBlurWritePolicy.shouldSuppressBlurRadiusWrite(true, 100));
    }
}
