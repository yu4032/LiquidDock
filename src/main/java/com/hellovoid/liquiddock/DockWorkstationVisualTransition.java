package com.hellovoid.liquiddock;

/**
 * Separates logical workstation mode from the radius geometry that is safe to commit to the
 * custom Dock stroke. Launcher 4.50 publishes several intermediate BlurBackground2 radii while
 * leaving laptop mode; those values may animate Prismal but must not become the ordinary stroke.
 */
final class DockWorkstationVisualTransition {
    enum Phase { NORMAL, WORKSTATION, EXITING_WORKSTATION }

    private static final DockWorkstationVisualTransition GLOBAL =
            new DockWorkstationVisualTransition();

    private Phase phase = Phase.NORMAL;
    private int generation;

    static DockWorkstationVisualTransition global() { return GLOBAL; }

    synchronized void initialize(boolean workstation) {
        phase = workstation ? Phase.WORKSTATION : Phase.NORMAL;
    }

    synchronized int onModeChanged(boolean entering) {
        // Launcher 4.50 emits onLaptopModeChanged(false) during ordinary process startup.
        // That is an idempotent state announcement, not a workstation-exit transition.
        if (entering) {
            if (phase == Phase.WORKSTATION) return generation;
            generation++;
            phase = Phase.WORKSTATION;
            return generation;
        }
        if (phase != Phase.WORKSTATION) return generation;
        generation++;
        phase = Phase.EXITING_WORKSTATION;
        return generation;
    }

    synchronized boolean shouldCommitStrokeGeometry() {
        return phase != Phase.EXITING_WORKSTATION;
    }

    synchronized boolean isExiting() {
        return phase == Phase.EXITING_WORKSTATION;
    }

    synchronized int generation() { return generation; }

    synchronized Phase phase() { return phase; }

    synchronized boolean settleExit(int candidateGeneration) {
        if (phase != Phase.EXITING_WORKSTATION || candidateGeneration != generation) return false;
        phase = Phase.NORMAL;
        return true;
    }
}
