package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DockMirrorShortcutVisibilityPolicyTest {
    @Test public void hiddenMirrorAlwaysOwnsItsLayoutSlotForCollapse() {
        assertTrue(DockMirrorShortcutVisibilityPolicy.shouldHideItem(
                true, 0, 6, DockMirrorShortcutVisibilityPolicy.MIRROR_VIEW_TYPE,
                DockMirrorShortcutVisibilityPolicy.NO_VIEW_TYPE));
        assertTrue(DockMirrorShortcutVisibilityPolicy.shouldHideItem(
                true, 5, 6, DockMirrorShortcutVisibilityPolicy.MIRROR_VIEW_TYPE,
                DockMirrorShortcutVisibilityPolicy.NO_VIEW_TYPE));
    }

    @Test public void onlyDividerImmediatelyBeforeTrailingMirrorIsCollapsed() {
        assertTrue(DockMirrorShortcutVisibilityPolicy.shouldHideItem(
                true, 4, 6, DockMirrorShortcutVisibilityPolicy.DIVIDER_VIEW_TYPE,
                DockMirrorShortcutVisibilityPolicy.MIRROR_VIEW_TYPE));

        assertFalse(DockMirrorShortcutVisibilityPolicy.shouldHideItem(
                true, 2, 6, DockMirrorShortcutVisibilityPolicy.DIVIDER_VIEW_TYPE,
                DockMirrorShortcutVisibilityPolicy.MIRROR_VIEW_TYPE));
        assertFalse(DockMirrorShortcutVisibilityPolicy.shouldHideItem(
                true, 4, 6, DockMirrorShortcutVisibilityPolicy.DIVIDER_VIEW_TYPE, 4));
    }

    @Test public void disablingFeatureRestoresMirrorAndDividerVisibility() {
        assertFalse(DockMirrorShortcutVisibilityPolicy.shouldHideItem(
                false, 5, 6, DockMirrorShortcutVisibilityPolicy.MIRROR_VIEW_TYPE,
                DockMirrorShortcutVisibilityPolicy.NO_VIEW_TYPE));
        assertFalse(DockMirrorShortcutVisibilityPolicy.shouldHideItem(
                false, 4, 6, DockMirrorShortcutVisibilityPolicy.DIVIDER_VIEW_TYPE,
                DockMirrorShortcutVisibilityPolicy.MIRROR_VIEW_TYPE));
    }

    @Test public void visibilityOwnershipIsLimitedToMirrorAndDividerTypes() {
        assertTrue(DockMirrorShortcutVisibilityPolicy.needsVisibilityOwnership(
                DockMirrorShortcutVisibilityPolicy.MIRROR_VIEW_TYPE));
        assertTrue(DockMirrorShortcutVisibilityPolicy.needsVisibilityOwnership(
                DockMirrorShortcutVisibilityPolicy.DIVIDER_VIEW_TYPE));
        assertFalse(DockMirrorShortcutVisibilityPolicy.needsVisibilityOwnership(4));
    }

    @Test public void compensationCancelsCenterJustifyDriftInBothLayoutDirections() {
        // LTR: removing a left/leading mirror shifts survivors left -> compensate right.
        assertEquals(1, DockMirrorShortcutVisibilityPolicy.compensationDirection(0, 6, false));
        // LTR: removing a right/trailing mirror shifts survivors right -> compensate left.
        assertEquals(-1, DockMirrorShortcutVisibilityPolicy.compensationDirection(5, 6, false));

        // RTL reverses physical main-axis direction.
        assertEquals(-1, DockMirrorShortcutVisibilityPolicy.compensationDirection(0, 6, true));
        assertEquals(1, DockMirrorShortcutVisibilityPolicy.compensationDirection(5, 6, true));
    }
}
