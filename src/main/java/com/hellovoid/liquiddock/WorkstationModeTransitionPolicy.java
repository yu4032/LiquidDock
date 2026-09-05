package com.hellovoid.liquiddock;

/** Pure Workstation mode probe/edge decisions shared by every Launcher authority source. */
final class WorkstationModeTransitionPolicy {
    private WorkstationModeTransitionPolicy() {}

    /**
     * Resolve the primary vendor probe first, then the compatibility fallback. A missing, failed,
     * null, or non-boolean probe is UNKNOWN rather than normal mode.
     */
    static Boolean resolveProbe(
            boolean primarySucceeded, Object primaryValue,
            boolean fallbackSucceeded, Object fallbackValue) {
        if (primarySucceeded && primaryValue instanceof Boolean) {
            return (Boolean) primaryValue;
        }
        if (fallbackSucceeded && fallbackValue instanceof Boolean) {
            return (Boolean) fallbackValue;
        }
        return null;
    }

    /** Only a known value different from the current mode owns a transition transaction. */
    static boolean shouldTransition(boolean currentMode, Boolean resolvedMode) {
        return resolvedMode != null && currentMode != resolvedMode.booleanValue();
    }
}
