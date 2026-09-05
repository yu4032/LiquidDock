package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlassRuntimeTransitionPolicyTest {
    @Test
    public void fullDisableProducesOneDominantTeardown() {
        GlassRuntimeTransitionPolicy.Snapshot before =
                new GlassRuntimeTransitionPolicy.Snapshot(true, true, true, true, true, true);
        GlassRuntimeTransitionPolicy.Snapshot after =
                new GlassRuntimeTransitionPolicy.Snapshot(false, false, false, false, false, false);

        GlassRuntimeTransitionPolicy.Transition transition =
                GlassRuntimeTransitionPolicy.plan(before, after);

        assertTrue(transition.fullTeardown);
        assertFalse(transition.iconRelease);
        assertFalse(transition.widgetRelease);
        assertFalse(transition.smallFolderRelease);
        assertFalse(transition.largeFolderRelease);
    }

    @Test
    public void darkContentToggleCarriesNewEffectiveValue() {
        GlassRuntimeTransitionPolicy.Snapshot before =
                new GlassRuntimeTransitionPolicy.Snapshot(true, true, true, true, true, true);
        GlassRuntimeTransitionPolicy.Snapshot after =
                new GlassRuntimeTransitionPolicy.Snapshot(true, true, true, false, true, true);

        GlassRuntimeTransitionPolicy.Transition transition =
                GlassRuntimeTransitionPolicy.plan(before, after);

        assertFalse(transition.fullTeardown);
        assertTrue(transition.widgetDarkContentChanged);
        assertFalse(transition.nextWidgetDarkContent);
    }

    @Test
    public void componentDisableReleasesOnlyItsEffectiveOwner() {
        GlassRuntimeTransitionPolicy.Snapshot before =
                new GlassRuntimeTransitionPolicy.Snapshot(true, true, true, false, true, true);
        GlassRuntimeTransitionPolicy.Snapshot after =
                new GlassRuntimeTransitionPolicy.Snapshot(true, false, true, false, true, true);

        GlassRuntimeTransitionPolicy.Transition transition =
                GlassRuntimeTransitionPolicy.plan(before, after);

        assertTrue(transition.iconRelease);
        assertFalse(transition.widgetRelease);
        assertFalse(transition.smallFolderRelease);
        assertFalse(transition.largeFolderRelease);
    }
}
