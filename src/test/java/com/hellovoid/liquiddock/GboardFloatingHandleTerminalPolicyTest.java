package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Runtime regression coverage for Gboard's bottom-handle terminal release policy. */
public class GboardFloatingHandleTerminalPolicyTest {
    @Test public void disabledAutoResizeCancelsAnyTerminalHandleRelease() {
        assertTrue(GboardFloatingHandlePolicy.shouldCancelTerminalRelease(true, false));
    }

    @Test public void enabledAutoResizeKeepsVendorTerminalRelease() {
        assertFalse(GboardFloatingHandlePolicy.shouldCancelTerminalRelease(true, true));
    }

    @Test public void nonTerminalEventsAreNeverCancelledByTerminalPolicy() {
        assertFalse(GboardFloatingHandlePolicy.shouldCancelTerminalRelease(false, false));
    }
}
