package com.hellovoid.liquiddock;

/** Minimal one-way SystemUI -> Launcher unlock handoff protocol. */
final class SystemUiKeyguardGoneProtocol {
    static final String ACTION =
            "com.hellovoid.liquiddock.action.SYSTEMUI_LOCKSCREEN_GONE_FINISHED";
    static final String LAUNCHER_PACKAGE = "com.miui.home";
    static final String SENDER_PERMISSION = "android.permission.STATUS_BAR_SERVICE";

    private SystemUiKeyguardGoneProtocol() {}
}
