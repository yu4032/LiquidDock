package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure ownership contract for overlapping Workstation recovery and rotation transitions. */
public class LauncherGlassProducerTransitionOwnershipTest {
    @Test
    public void workstationOwnsTransitionOnlyWhenRotationDoesNotAlreadyOwnIt() {
        assertTrue(LauncherGlassProducerTransitionPolicy.workstationCanOwnEndpointTransition(false));
        assertFalse(LauncherGlassProducerTransitionPolicy.workstationCanOwnEndpointTransition(true));
    }
}
