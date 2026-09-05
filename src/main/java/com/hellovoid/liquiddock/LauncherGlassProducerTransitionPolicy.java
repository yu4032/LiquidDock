package com.hellovoid.liquiddock;

/** Decides ownership and rollover of Workspace PassBlur producer endpoint transitions. */
final class LauncherGlassProducerTransitionPolicy {
    private LauncherGlassProducerTransitionPolicy() {}

    static boolean requiresEndpointRollover(
            int previousRotation, int nextRotation, boolean surfaceChanged) {
        return surfaceChanged || previousRotation != nextRotation;
    }

    /** Rotation owns the endpoint transition while its Shell settle barrier is active. */
    static boolean workstationCanOwnEndpointTransition(boolean rotationTransitionPending) {
        return !rotationTransitionPending;
    }
}
