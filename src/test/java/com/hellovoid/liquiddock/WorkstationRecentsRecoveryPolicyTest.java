package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure fail-closed policy for Workstation Recents producer recovery. */
public class WorkstationRecentsRecoveryPolicyTest {
    @Test
    public void normalModeUncoversWithoutRollover() {
        WorkstationRecentsRecoveryPolicy.Decision decision =
                WorkstationRecentsRecoveryPolicy.onRecentsReturn(false, false);
        assertFalse(decision.requestRollover);
        assertTrue(decision.allowUncover);
    }

    @Test
    public void workstationRejectStaysFailClosed() {
        WorkstationRecentsRecoveryPolicy.Decision decision =
                WorkstationRecentsRecoveryPolicy.onRecentsReturn(true, false);
        assertTrue(decision.requestRollover);
        assertFalse(decision.allowUncover);
    }

    @Test
    public void workstationAcceptedRolloverAllowsFreshnessRecoveryButNotVisibilityByItself() {
        WorkstationRecentsRecoveryPolicy.Decision decision =
                WorkstationRecentsRecoveryPolicy.onRecentsReturn(true, true);
        assertTrue(decision.requestRollover);
        assertTrue(decision.allowUncover);
    }

    @Test
    public void workstationRolloverOnlyRunsForAuthoritativeRecentsCoverage() {
        assertTrue(WorkstationRecentsRecoveryPolicy.shouldRequestRollover(true, true));
        assertFalse(WorkstationRecentsRecoveryPolicy.shouldRequestRollover(true, false));
    }

    @Test
    public void normalModeNeverRequestsWorkstationRollover() {
        assertFalse(WorkstationRecentsRecoveryPolicy.shouldRequestRollover(false, true));
        assertFalse(WorkstationRecentsRecoveryPolicy.shouldRequestRollover(false, false));
    }
}
