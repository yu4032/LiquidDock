package com.hellovoid.liquiddock;

/** Android-free policy for deciding whether Recents may uncover the shared glass scene. */
final class WorkstationRecentsRecoveryPolicy {
    static final class Decision {
        final boolean requestRollover;
        final boolean allowUncover;

        Decision(boolean requestRollover, boolean allowUncover) {
            this.requestRollover = requestRollover;
            this.allowUncover = allowUncover;
        }
    }

    private WorkstationRecentsRecoveryPolicy() {}

    /** Only a currently Recents-covered Workstation scene owns a producer rollover request. */
    static boolean shouldRequestRollover(boolean workstationMode, boolean recentsCovered) {
        return workstationMode && recentsCovered;
    }

    static Decision onRecentsReturn(boolean workstationMode, boolean rolloverAccepted) {
        if (!workstationMode) return new Decision(false, true);
        return new Decision(true, rolloverAccepted);
    }
}
