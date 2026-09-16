package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Runtime contract for hiding Gboard's floating-keyboard docking effects without blocking drag. */
public class GboardFloatingBottomDockPolicyTest {
    @Test public void disabledBottomDockingMasksDockIconLookup() {
        assertTrue(GboardFloatingHandlePolicy.shouldMaskDockHitResult(
                false, ".icon.floating_keyboard_dock_hint_v2"));
    }

    @Test public void enabledBottomDockingPreservesDockIconLookup() {
        assertFalse(GboardFloatingHandlePolicy.shouldMaskDockHitResult(
                true, ".icon.floating_keyboard_dock_hint_v2"));
    }

    @Test public void disabledBottomDockingHidesEveryDockHintLayer() {
        assertTrue(GboardFloatingHandlePolicy.shouldForceDockEffectInvisible(
                false, ".floating_keyboard_dock_hint_v2"));
        assertTrue(GboardFloatingHandlePolicy.shouldForceDockEffectInvisible(
                false, ".floating_keyboard_dock_hint_v2_animated_color_default"));
        assertTrue(GboardFloatingHandlePolicy.shouldForceDockEffectInvisible(
                false, ".floating_keyboard_dock_hint_v2_animated_color_expanded"));
        assertTrue(GboardFloatingHandlePolicy.shouldForceDockEffectInvisible(
                false, ".icon.floating_keyboard_dock_hint_v2"));
    }

    @Test public void enabledBottomDockingDoesNotHideDockHintLayers() {
        assertFalse(GboardFloatingHandlePolicy.shouldForceDockEffectInvisible(
                true, ".floating_keyboard_dock_hint_v2"));
    }

    @Test public void unrelatedViewsAreNeverHidden() {
        assertFalse(GboardFloatingHandlePolicy.shouldForceDockEffectInvisible(false, null));
        assertFalse(GboardFloatingHandlePolicy.shouldForceDockEffectInvisible(
                false, ".some_other_view"));
    }

    @Test public void disabledBottomDockingSuppressesOnlyDockingHaptic() {
        assertTrue(GboardFloatingHandlePolicy.shouldSuppressDockHaptic(false, true));
        assertFalse(GboardFloatingHandlePolicy.shouldSuppressDockHaptic(false, false));
        assertFalse(GboardFloatingHandlePolicy.shouldSuppressDockHaptic(true, true));
    }

    @Test public void capturedDockTargetMayMoveThroughHiddenHintRegion() {
        assertEquals(600, GboardFloatingHandlePolicy.effectiveDragClampHeight(
                false, true, 760, 160));
        assertEquals(0, GboardFloatingHandlePolicy.effectiveDragClampHeight(
                false, true, 120, 160));
    }

    @Test public void clampHeightStaysNativeUntilDockTargetIsCaptured() {
        assertEquals(760, GboardFloatingHandlePolicy.effectiveDragClampHeight(
                false, false, 760, 160));
        assertEquals(760, GboardFloatingHandlePolicy.effectiveDragClampHeight(
                true, true, 760, 160));
    }

    @Test public void disabledBottomDockingCancelsTerminalRelease() {
        assertTrue(GboardFloatingHandlePolicy.shouldCancelBottomDockRelease(true, false));
        assertFalse(GboardFloatingHandlePolicy.shouldCancelBottomDockRelease(false, false));
        assertFalse(GboardFloatingHandlePolicy.shouldCancelBottomDockRelease(true, true));
    }
}
