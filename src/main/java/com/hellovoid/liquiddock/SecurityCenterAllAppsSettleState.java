package com.hellovoid.liquiddock;

/**
 * Generation-safe All Apps settlement gate.
 *
 * <p>The vendor all-apps-present flag is target authority only. A transition becomes settleable
 * only after a custom frame for the same generation has actually been presented with a node
 * composition matching that target.</p>
 */
final class SecurityCenterAllAppsSettleState {
    static final class Decision {
        final boolean settle;
        final boolean allAppsPresent;
        final long generation;

        private Decision(boolean settle, boolean allAppsPresent, long generation) {
            this.settle = settle;
            this.allAppsPresent = allAppsPresent;
            this.generation = generation;
        }

        static Decision none(long generation) {
            return new Decision(false, false, generation);
        }

        static Decision settle(boolean allAppsPresent, long generation) {
            return new Decision(true, allAppsPresent, generation);
        }
    }

    private long generation = -1L;
    private Boolean allAppsPresent;

    synchronized void onTransitionStarted(long transitionGeneration) {
        if (transitionGeneration < 0L) return;
        generation = transitionGeneration;
        allAppsPresent = null;
    }

    synchronized void onTargetResolved(long transitionGeneration, boolean targetAllAppsPresent) {
        if (transitionGeneration < 0L || transitionGeneration != generation) return;
        allAppsPresent = targetAllAppsPresent;
    }

    synchronized Decision onFramePresented(long frameGeneration, boolean frameHasAllApps) {
        if (frameGeneration < 0L || frameGeneration != generation || allAppsPresent == null
                || allAppsPresent.booleanValue() != frameHasAllApps) {
            return Decision.none(generation);
        }
        boolean target = allAppsPresent.booleanValue();
        long settledGeneration = generation;
        resetLocked();
        return Decision.settle(target, settledGeneration);
    }

    synchronized void reset() {
        resetLocked();
    }

    private void resetLocked() {
        generation = -1L;
        allAppsPresent = null;
    }
}
