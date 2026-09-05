package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure authority/episode behavior for Workstation Recents producer recovery. */
public class WorkstationRecentsRecoveryPolicyTest {
    @Test
    public void nonCoveredHideHasNoAuthority() {
        WorkstationRecentsRecoveryPolicy policy = new WorkstationRecentsRecoveryPolicy();

        WorkstationRecentsRecoveryPolicy.Decision decision =
                policy.onRecentViewHide(true, false);

        assertFalse(decision.authoritative);
        assertFalse(decision.requestRollover);
        assertFalse(decision.allowUncover);
        assertEquals(WorkstationRecentsRecoveryPolicy.Phase.IDLE, policy.phase());
    }

    @Test
    public void normalModeCoveredHideUncoversWithoutWorkstationRollover() {
        WorkstationRecentsRecoveryPolicy policy = new WorkstationRecentsRecoveryPolicy();
        policy.onRecentViewShow();

        WorkstationRecentsRecoveryPolicy.Decision decision =
                policy.onRecentViewHide(false, true);

        assertTrue(decision.authoritative);
        assertFalse(decision.requestRollover);
        assertTrue(decision.allowUncover);
        assertEquals(WorkstationRecentsRecoveryPolicy.Phase.IDLE, policy.phase());
    }

    @Test
    public void workstationCoveredHideCreatesExactlyOneRecoveryEpisode() {
        WorkstationRecentsRecoveryPolicy policy = new WorkstationRecentsRecoveryPolicy();
        policy.onRecentViewShow();

        WorkstationRecentsRecoveryPolicy.Decision first =
                policy.onRecentViewHide(true, true);
        WorkstationRecentsRecoveryPolicy.Decision duplicate =
                policy.onRecentViewHide(true, true);

        assertTrue(first.authoritative);
        assertTrue(first.requestRollover);
        assertFalse(first.allowUncover);
        assertTrue(first.episode > 0L);
        assertFalse(duplicate.authoritative);
        assertFalse(duplicate.requestRollover);
        assertEquals(first.episode, duplicate.episode);
        assertEquals(WorkstationRecentsRecoveryPolicy.Phase.RECOVERING, policy.phase());
    }

    @Test
    public void matchingAcceptedTerminalAloneAllowsUncover() {
        WorkstationRecentsRecoveryPolicy policy = new WorkstationRecentsRecoveryPolicy();
        policy.onRecentViewShow();
        WorkstationRecentsRecoveryPolicy.Decision start =
                policy.onRecentViewHide(true, true);

        WorkstationRecentsRecoveryPolicy.TerminalDecision terminal =
                policy.onRecoveryTerminal(
                        start.episode, LauncherGlassProducerRecoveryState.Result.ACCEPTED);

        assertTrue(terminal.matched);
        assertTrue(terminal.allowUncover);
        assertFalse(terminal.failClosed);
        assertEquals(WorkstationRecentsRecoveryPolicy.Phase.IDLE, policy.phase());
    }

    @Test
    public void rejectedOrFailedTerminalStaysFailClosedUntilNewShow() {
        WorkstationRecentsRecoveryPolicy policy = new WorkstationRecentsRecoveryPolicy();
        policy.onRecentViewShow();
        WorkstationRecentsRecoveryPolicy.Decision start =
                policy.onRecentViewHide(true, true);

        WorkstationRecentsRecoveryPolicy.TerminalDecision terminal =
                policy.onRecoveryTerminal(
                        start.episode, LauncherGlassProducerRecoveryState.Result.FAILED);

        assertTrue(terminal.matched);
        assertFalse(terminal.allowUncover);
        assertTrue(terminal.failClosed);
        assertEquals(WorkstationRecentsRecoveryPolicy.Phase.FAILED_CLOSED, policy.phase());
        assertFalse(policy.onRecentViewHide(true, true).authoritative);

        policy.onRecentViewShow();
        assertEquals(WorkstationRecentsRecoveryPolicy.Phase.COVERED, policy.phase());
        assertTrue(policy.onRecentViewHide(true, true).authoritative);
    }

    @Test
    public void staleOldTerminalCannotCompleteCurrentEpisode() {
        WorkstationRecentsRecoveryPolicy policy = new WorkstationRecentsRecoveryPolicy();
        policy.onRecentViewShow();
        WorkstationRecentsRecoveryPolicy.Decision old =
                policy.onRecentViewHide(true, true);
        policy.onRecoveryTerminal(old.episode, LauncherGlassProducerRecoveryState.Result.FAILED);

        policy.onRecentViewShow();
        WorkstationRecentsRecoveryPolicy.Decision current =
                policy.onRecentViewHide(true, true);
        WorkstationRecentsRecoveryPolicy.TerminalDecision stale =
                policy.onRecoveryTerminal(
                        old.episode, LauncherGlassProducerRecoveryState.Result.ACCEPTED);

        assertFalse(stale.matched);
        assertFalse(stale.allowUncover);
        assertEquals(current.episode, policy.activeEpisode());
        assertEquals(WorkstationRecentsRecoveryPolicy.Phase.RECOVERING, policy.phase());
    }
}
