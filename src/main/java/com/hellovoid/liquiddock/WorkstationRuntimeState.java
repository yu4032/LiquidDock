package com.hellovoid.liquiddock;

/** Process-local authoritative Workstation mode state. */
final class WorkstationRuntimeState {
    private static volatile boolean active;

    private WorkstationRuntimeState() {}

    static boolean isActive() {
        return active;
    }

    static boolean publish(boolean enabled) {
        boolean changed = active != enabled;
        active = enabled;
        return changed;
    }

    static void resetForTest() {
        active = false;
    }
}
