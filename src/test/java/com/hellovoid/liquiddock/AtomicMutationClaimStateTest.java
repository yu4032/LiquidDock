package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure all-or-nothing ownership decisions for vendor mutations that create restoration claims. */
public class AtomicMutationClaimStateTest {
    @Test public void mutationCannotBeginUntilEveryTargetResolved() {
        AtomicMutationClaimState state = new AtomicMutationClaimState(2);

        assertFalse(state.beginIfFullyResolved(1));
        assertTrue(state.beginIfFullyResolved(2));
    }

    @Test public void everySuccessfulMutationCommitsOnlyAfterLastTarget() {
        AtomicMutationClaimState state = new AtomicMutationClaimState(2);
        assertTrue(state.beginIfFullyResolved(2));

        AtomicMutationClaimState.Decision first = state.onMutationResult(true);
        assertTrue(first.continueMutation);
        assertFalse(first.commitClaim);
        assertEquals(0, first.rollbackCount);

        AtomicMutationClaimState.Decision second = state.onMutationResult(true);
        assertFalse(second.continueMutation);
        assertTrue(second.commitClaim);
        assertEquals(0, second.rollbackCount);
    }

    @Test public void failedSecondMutationRollsBackEveryAttemptedTarget() {
        AtomicMutationClaimState state = new AtomicMutationClaimState(3);
        assertTrue(state.beginIfFullyResolved(3));
        state.onMutationResult(true);

        AtomicMutationClaimState.Decision failed = state.onMutationResult(false);

        assertFalse(failed.continueMutation);
        assertFalse(failed.commitClaim);
        assertEquals(2, failed.rollbackCount);
    }

    @Test public void failedSingleMutationNeverCreatesClaim() {
        AtomicMutationClaimState state = new AtomicMutationClaimState(1);
        assertTrue(state.beginIfFullyResolved(1));

        AtomicMutationClaimState.Decision failed = state.onMutationResult(false);

        assertFalse(failed.commitClaim);
        assertEquals(1, failed.rollbackCount);
    }

    @Test public void mutationResultBeforeResolutionCannotCommit() {
        AtomicMutationClaimState state = new AtomicMutationClaimState(1);

        AtomicMutationClaimState.Decision ignored = state.onMutationResult(true);

        assertFalse(ignored.continueMutation);
        assertFalse(ignored.commitClaim);
        assertEquals(0, ignored.rollbackCount);
    }
}
