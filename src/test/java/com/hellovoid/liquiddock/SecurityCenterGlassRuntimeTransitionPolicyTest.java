package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SecurityCenterGlassRuntimeTransitionPolicyTest {
    @Test
    public void effectiveStateRequiresCoreGlassAndSecurityCenterSwitches() {
        assertTrue(new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, true, true)
                .effective());
        assertFalse(new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(false, true, true)
                .effective());
        assertFalse(new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, false, true)
                .effective());
        assertFalse(new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, true, false)
                .effective());
    }

    @Test
    public void anyEffectiveDisableReleasesAllOwnership() {
        SecurityCenterGlassRuntimeTransitionPolicy.Snapshot before =
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, true, true);

        assertTrue(SecurityCenterGlassRuntimeTransitionPolicy.plan(before,
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(false, true, true))
                .releaseAll);
        assertTrue(SecurityCenterGlassRuntimeTransitionPolicy.plan(before,
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, false, true))
                .releaseAll);
        assertTrue(SecurityCenterGlassRuntimeTransitionPolicy.plan(before,
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, true, false))
                .releaseAll);
    }

    @Test
    public void inactiveTransitionsDoNotReleaseAllOwnership() {
        SecurityCenterGlassRuntimeTransitionPolicy.Snapshot inactive =
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(false, true, true);

        assertFalse(SecurityCenterGlassRuntimeTransitionPolicy.plan(inactive,
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(false, false, false))
                .releaseAll);
        assertFalse(SecurityCenterGlassRuntimeTransitionPolicy.plan(inactive,
                new SecurityCenterGlassRuntimeTransitionPolicy.Snapshot(true, true, true))
                .releaseAll);
    }
}
