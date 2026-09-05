package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure authority/fail-closed behavior for Workstation Recents producer recovery. */
public class WorkstationRecentsRecoveryPolicyTest {
    @Test
    public void nonCoveredHideHasNoAuthority() {
        WorkstationRecentsRecoveryPolicy policy = new WorkstationRecentsRecoveryPolicy();

        WorkstationRecentsRecoveryPolicy.Decision decision =
                policy.onRecentViewHide(true, false);

        assertFalse(decision.authoritative);
        assertFalse(decision.requestRollover);
        assertFalse(decision.allowUncover);
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
    }

    @Test
    public void workstationCoveredHideStartsExactlyOneRecoveryEpisode() {
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
        assertFalse(duplicate.allowUncover);
    }

    @Test
    public void matchingAcceptedTerminalAllowsUncoverOnlyAfterRecoveryCompletes() {
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
    }

    @Test
    public void rejectedOrFailedTerminalRemainsFailClosed() {
        WorkstationRecentsRecoveryPolicy rejectedPolicy = new WorkstationRecentsRecoveryPolicy();
        rejectedPolicy.onRecentViewShow();
        long rejectedEpisode = rejectedPolicy.onRecentViewHide(true, true).episode;
        WorkstationRecentsRecoveryPolicy.TerminalDecision rejected =
                rejectedPolicy.onRecoveryTerminal(
                        rejectedEpisode, LauncherGlassProducerRecoveryState.Result.REJECTED);

        assertTrue(rejected.matched);
        assertFalse(rejected.allowUncover);
        assertTrue(rejected.failClosed);

        WorkstationRecentsRecoveryPolicy failedPolicy = new WorkstationRecentsRecoveryPolicy();
        failedPolicy.onRecentViewShow();
        long failedEpisode = failedPolicy.onRecentViewHide(true, true).episode;
        WorkstationRecentsRecoveryPolicy.TerminalDecision failed =
                failedPolicy.onRecoveryTerminal(
                        failedEpisode, LauncherGlassProducerRecoveryState.Result.FAILED);

        assertTrue(failed.matched);
        assertFalse(failed.allowUncover);
        assertTrue(failed.failClosed);
    }

    @Test
    public void staleTerminalCannotCompleteNewEpisode() {
        WorkstationRecentsRecoveryPolicy policy = new WorkstationRecentsRecoveryPolicy();
        policy.onRecentViewShow();
        long first = policy.onRecentViewHide(true, true).episode;
        policy.onRecoveryTerminal(first, LauncherGlassProducerRecoveryState.Result.FAILED);

        policy.onRecentViewShow();
        long second = policy.onRecentViewHide(true, true).episode;
        WorkstationRecentsRecoveryPolicy.TerminalDecision stale =
                policy.onRecoveryTerminal(first, LauncherGlassProducerRecoveryState.Result.ACCEPTED);
        WorkstationRecentsRecoveryPolicy.TerminalDecision current =
                policy.onRecoveryTerminal(second, LauncherGlassProducerRecoveryState.Result.ACCEPTED);

        assertFalse(stale.matched);
        assertFalse(stale.allowUncover);
        assertTrue(current.matched);
        assertTrue(current.allowUncover);
    }

    @Test
    public void producerRecoverySuccessStillWaitsForSceneFreshFrame() {
        WorkstationRecentsRecoveryPolicy policy = new WorkstationRecentsRecoveryPolicy();
        LauncherGlassSceneController.StateMachine scene =
                new LauncherGlassSceneController.StateMachine();
        scene.onRootReady();
        scene.onBootstrapReconciled();
        scene.setCovered(true);
        policy.onRecentViewShow();

        WorkstationRecentsRecoveryPolicy.Decision start =
                policy.onRecentViewHide(true, true);
        WorkstationRecentsRecoveryPolicy.TerminalDecision terminal =
                policy.onRecoveryTerminal(
                        start.episode, LauncherGlassProducerRecoveryState.Result.ACCEPTED);
        assertTrue(terminal.allowUncover);

        scene.setCovered(false);
        long generation = scene.generation();
        assertFalse(scene.isLayerVisible());

        scene.onFreshFrameReady(generation);
        assertTrue(scene.isLayerVisible());
    }
}
