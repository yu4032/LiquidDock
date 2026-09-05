package com.hellovoid.liquiddock;

/** Pure guards for delayed per-orientation workspace-memory work. */
final class HomeGridOrientationMemoryPolicy {
    private HomeGridOrientationMemoryPolicy() {}

    static boolean shouldResolve(
            boolean workstationMode,
            boolean sameWorkspace,
            long scheduledGeneration,
            long currentGeneration,
            HomeGridOrientation targetOrientation,
            HomeGridOrientation currentOrientation) {
        return !workstationMode
                && sameWorkspace
                && scheduledGeneration == currentGeneration
                && targetOrientation != null
                && targetOrientation == currentOrientation;
    }
}
