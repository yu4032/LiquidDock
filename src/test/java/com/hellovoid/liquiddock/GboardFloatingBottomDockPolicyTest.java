package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Runtime contract for blocking Gboard's floating-keyboard dock predicate without blocking drag. */
public class GboardFloatingBottomDockPolicyTest {
    @Test public void disabledBottomDockingMasksDockIconLookup() {
        assertTrue(GboardFloatingHandlePolicy.shouldMaskDockHitResult(
                false, ".icon.floating_keyboard_dock_hint_v2"));
    }

    @Test public void enabledBottomDockingPreservesDockIconLookup() {
        assertFalse(GboardFloatingHandlePolicy.shouldMaskDockHitResult(
                true, ".icon.floating_keyboard_dock_hint_v2"));
    }

    @Test public void disabledBottomDockingDoesNotMaskDockHintRoot() {
        assertFalse(GboardFloatingHandlePolicy.shouldMaskDockHitResult(
                false, ".floating_keyboard_dock_hint_v2"));
    }

    @Test public void disabledBottomDockingDoesNotMaskUnrelatedViews() {
        assertFalse(GboardFloatingHandlePolicy.shouldMaskDockHitResult(false, null));
        assertFalse(GboardFloatingHandlePolicy.shouldMaskDockHitResult(false, ".some_other_view"));
    }
}
