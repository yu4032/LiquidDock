package com.hellovoid.liquiddock;

/** Explicit SystemUI -> Launcher timing handoff for WMShell HOME transitions. */
final class SystemUiHomeTransitionProtocol {
    static final String ACTION =
            "com.hellovoid.liquiddock.action.SYSTEMUI_HOME_TRANSITION";
    static final String LAUNCHER_PACKAGE = "com.miui.home";
    static final String SENDER_PERMISSION = "android.permission.STATUS_BAR_SERVICE";

    static final String EXTRA_PHASE = "phase";
    static final String EXTRA_HOME_VISIBLE = "homeVisible";
    static final String EXTRA_SERIAL = "serial";
    static final String EXTRA_EVENT_TIME_NANOS = "eventTimeNanos";
    static final String EXTRA_ABORTED = "aborted";

    static final int PHASE_START = 1;
    static final int PHASE_FINISH = 2;

    private SystemUiHomeTransitionProtocol() {}

    static boolean isKnownPhase(int phase) {
        return phase == PHASE_START || phase == PHASE_FINISH;
    }
}
