package com.hellovoid.liquiddock;

/** Decides when a Workspace PassBlur producer endpoint must start a new native generation. */
final class LauncherGlassProducerTransitionPolicy {
    private LauncherGlassProducerTransitionPolicy() {}

    static boolean requiresEndpointRollover(
            int previousRotation, int nextRotation, boolean surfaceChanged) {
        return surfaceChanged || previousRotation != nextRotation;
    }
}
