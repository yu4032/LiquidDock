package com.hellovoid.liquiddock;

/**
 * Android-free all-or-nothing transaction state for vendor mutations that establish ownership.
 * Resolution is a separate phase: no mutation may begin until every configured target exists.
 */
final class AtomicMutationClaimState {
    static final class Decision {
        final boolean continueMutation;
        final boolean commitClaim;
        final int rollbackCount;

        Decision(boolean continueMutation, boolean commitClaim, int rollbackCount) {
            this.continueMutation = continueMutation;
            this.commitClaim = commitClaim;
            this.rollbackCount = rollbackCount;
        }
    }

    private final int targetCount;
    private boolean mutationStarted;
    private boolean terminal;
    private int attemptedCount;

    AtomicMutationClaimState(int targetCount) {
        this.targetCount = Math.max(0, targetCount);
    }

    boolean beginIfFullyResolved(int resolvedCount) {
        if (terminal || mutationStarted || targetCount <= 0 || resolvedCount != targetCount) {
            return false;
        }
        mutationStarted = true;
        return true;
    }

    Decision onMutationResult(boolean succeeded) {
        if (!mutationStarted || terminal) return new Decision(false, false, 0);

        attemptedCount++;
        if (!succeeded) {
            terminal = true;
            return new Decision(false, false, attemptedCount);
        }
        if (attemptedCount >= targetCount) {
            terminal = true;
            return new Decision(false, true, 0);
        }
        return new Decision(true, false, 0);
    }
}
