package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Runtime contract for blocking Gboard's floating-keyboard bottom docking gesture. */
public class GboardFloatingBottomDockPolicyTest {
    @Test public void disabledBottomDockingSuppressesMoveInsideDockZone() {
        assertTrue(GboardFloatingHandlePolicy.shouldSuppressDockMove(
                false, 900f, 800));
    }

    @Test public void disabledBottomDockingKeepsNormalDragAboveDockZone() {
        assertFalse(GboardFloatingHandlePolicy.shouldSuppressDockMove(
                false, 700f, 800));
    }

    @Test public void enabledBottomDockingPreservesVendorBehavior() {
        assertFalse(GboardFloatingHandlePolicy.shouldSuppressDockMove(
                true, 900f, 800));
    }

    @Test public void unresolvedDockZoneFailsOpen() {
        assertFalse(GboardFloatingHandlePolicy.shouldSuppressDockMove(
                false, 900f, Integer.MAX_VALUE));
    }
}
