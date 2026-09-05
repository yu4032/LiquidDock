package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure ownership rule for rotation versus Workstation producer transitions. */
public class LauncherGlassProducerTransitionOwnershipTest {
    @Test
    public void workstationOwnsEndpointTransitionOnlyWhenRotationDoesNot() {
        assertTrue(LauncherGlassProducerTransitionPolicy.workstationCanOwnEndpointTransition(false));
        assertFalse(LauncherGlassProducerTransitionPolicy.workstationCanOwnEndpointTransition(true));
    }
}
