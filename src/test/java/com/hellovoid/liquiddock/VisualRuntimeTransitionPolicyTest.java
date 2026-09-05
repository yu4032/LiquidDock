package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VisualRuntimeTransitionPolicyTest {
    @Test
    public void disablingCoreReleasesEveryEffectiveDockOwner() {
        VisualRuntimeTransitionPolicy.Snapshot before =
                new VisualRuntimeTransitionPolicy.Snapshot(true, true, true, true, true, true);
        VisualRuntimeTransitionPolicy.Snapshot after =
                new VisualRuntimeTransitionPolicy.Snapshot(false, false, false, false, false, false);

        VisualRuntimeTransitionPolicy.Transition transition =
                VisualRuntimeTransitionPolicy.plan(before, after);

        assertTrue(transition.dockCustomizationDisabled);
        assertTrue(transition.strokeDisabled);
        assertTrue(transition.dockShadowDisabled);
        assertTrue(transition.strokeShadowChanged);
        assertTrue(transition.dividerDisabled);
        assertTrue(transition.mirrorVisibilityChanged);
        assertFalse(transition.strokeEnabled);
        assertFalse(transition.dockShadowEnabled);
    }

    @Test
    public void enablingOnlyStrokeDoesNotClaimDockShadowOwnership() {
        VisualRuntimeTransitionPolicy.Snapshot before =
                new VisualRuntimeTransitionPolicy.Snapshot(true, false, false, false, true, false);
        VisualRuntimeTransitionPolicy.Snapshot after =
                new VisualRuntimeTransitionPolicy.Snapshot(true, true, false, false, true, false);

        VisualRuntimeTransitionPolicy.Transition transition =
                VisualRuntimeTransitionPolicy.plan(before, after);

        assertTrue(transition.strokeEnabled);
        assertFalse(transition.dockShadowEnabled);
        assertFalse(transition.dockCustomizationDisabled);
    }
}
