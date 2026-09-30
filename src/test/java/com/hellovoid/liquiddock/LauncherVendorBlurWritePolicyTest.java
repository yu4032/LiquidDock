package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherVendorBlurWritePolicyTest {
    @Test public void unownedVendorWritesAlwaysPassThrough() {
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressPassWindowWrite(false, true));
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressPassWindowWrite(false, false));
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressPositiveBlurWrite(false, 1));
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressPositiveBlurWrite(false, 100));
    }

    @Test public void liquidDockOwnerSuppressesOnlyBlurEnablingWrites() {
        assertTrue(LauncherVendorBlurWritePolicy.shouldSuppressPassWindowWrite(true, true));
        assertTrue(LauncherVendorBlurWritePolicy.shouldSuppressPositiveBlurWrite(true, 1));
        assertTrue(LauncherVendorBlurWritePolicy.shouldSuppressPositiveBlurWrite(true, 100));
    }

    @Test public void vendorDisableWritesRemainAuthoritative() {
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressPassWindowWrite(true, false));
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressPositiveBlurWrite(true, 0));
        assertFalse(LauncherVendorBlurWritePolicy.shouldSuppressPositiveBlurWrite(true, -1));
    }
}
