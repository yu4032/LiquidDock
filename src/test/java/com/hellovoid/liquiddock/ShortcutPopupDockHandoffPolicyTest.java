package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShortcutPopupDockHandoffPolicyTest {
    @Test
    public void dockMenuCanCrossViewRootsOnTheSameDisplay() {
        // Root/view identity is deliberately absent from the contract.
        assertTrue(ShortcutPopupDockHandoffPolicy.matchesDisplay(true, 0, 0));
        assertTrue(ShortcutPopupDockHandoffPolicy.matchesDisplay(true, 2, 2));
    }

    @Test
    public void differentOrUnknownDisplaysNeverBorrowTheDockCapture() {
        assertFalse(ShortcutPopupDockHandoffPolicy.matchesDisplay(true, 0, 1));
        assertFalse(ShortcutPopupDockHandoffPolicy.matchesDisplay(true, -1, 0));
        assertFalse(ShortcutPopupDockHandoffPolicy.matchesDisplay(true, 0, -1));
        assertFalse(ShortcutPopupDockHandoffPolicy.matchesDisplay(false, 0, 0));
    }

    @Test
    public void directShowAdoptsOnlyAValidUnclaimedDockTouchCapture() {
        assertTrue(ShortcutPopupDockHandoffPolicy.canAdoptOnShow(true, false, true, false));
        assertFalse(ShortcutPopupDockHandoffPolicy.canAdoptOnShow(false, false, true, false));
        assertFalse(ShortcutPopupDockHandoffPolicy.canAdoptOnShow(true, true, true, false));
        assertFalse(ShortcutPopupDockHandoffPolicy.canAdoptOnShow(true, false, false, false));
        assertFalse(ShortcutPopupDockHandoffPolicy.canAdoptOnShow(true, false, true, true));
    }
}
