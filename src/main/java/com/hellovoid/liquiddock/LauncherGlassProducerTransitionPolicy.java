package com.hellovoid.liquiddock;

/** Decides ownership and rollover of Workspace PassBlur producer endpoint transitions. */
final class LauncherGlassProducerTransitionPolicy {
    private LauncherGlassProducerTransitionPolicy() {}

    static boolean requiresEndpointRollover(
            int previousRotation, int nextRotation, boolean surfaceChanged) {
        return surfaceChanged || previousRotation != nextRotation;
    }

    /** Rotation owns the explicit endpoint transition while its Shell settle barrier is active. */
    static boolean workstationCanOwnEndpointTransition(boolean rotationTransitionPending) {
        return !rotationTransitionPending;
    }

    /** Generic EGL/bootstrap work may create an endpoint only when no explicit transition owns it. */
    static boolean canCreateProducerEndpoint(boolean explicitTransitionOwned) {
        return !explicitTransitionOwned;
    }

    /** A delayed endpoint callback belongs only to the transition epoch that created it. */
    static boolean isEndpointCallbackCurrent(long callbackEpoch, long currentEpoch) {
        return callbackEpoch == currentEpoch;
    }
}
