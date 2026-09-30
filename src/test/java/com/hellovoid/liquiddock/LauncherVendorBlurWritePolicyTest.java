package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LauncherVendorBlurWritePolicyTest {
    @Test public void unownedVendorWritesPassThrough() {
        assertTrue(LauncherVendorBlurWritePolicy.passWindowBlurEnabled(false, true));
        assertFalse(LauncherVendorBlurWritePolicy.passWindowBlurEnabled(false, false));
        assertEquals(1, LauncherVendorBlurWritePolicy.blurModeOrRadius(false, 1));
        assertEquals(100, LauncherVendorBlurWritePolicy.blurModeOrRadius(false, 100));
    }

    @Test public void liquidDockOwnerSuppressesPositiveVendorBlurWrites() {
        assertFalse(LauncherVendorBlurWritePolicy.passWindowBlurEnabled(true, true));
        assertEquals(0, LauncherVendorBlurWritePolicy.blurModeOrRadius(true, 1));
        assertEquals(0, LauncherVendorBlurWritePolicy.blurModeOrRadius(true, 100));
    }

    @Test public void vendorDisableWritesRemainDisableWrites() {
        assertFalse(LauncherVendorBlurWritePolicy.passWindowBlurEnabled(true, false));
        assertEquals(0, LauncherVendorBlurWritePolicy.blurModeOrRadius(true, 0));
        assertEquals(-1, LauncherVendorBlurWritePolicy.blurModeOrRadius(true, -1));
    }
}
