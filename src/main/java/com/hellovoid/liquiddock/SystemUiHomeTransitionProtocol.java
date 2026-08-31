package com.hellovoid.liquiddock;

/** One-way SystemUI/WMShell -> Launcher HOME visibility timing protocol. */
final class SystemUiHomeTransitionProtocol {
    static final String ACTION =
            "com.hellovoid.liquiddock.action.SYSTEMUI_HOME_VISIBILITY_READY";
    static final String EXTRA_VISIBLE =
            "com.hellovoid.liquiddock.extra.HOME_VISIBLE";
    static final String EXTRA_SOURCE_UPTIME_MS =
            "com.hellovoid.liquiddock.extra.SOURCE_UPTIME_MS";
    static final String LAUNCHER_PACKAGE = "com.miui.home";
    static final String SENDER_PERMISSION = "android.permission.STATUS_BAR_SERVICE";

    private SystemUiHomeTransitionProtocol() {}
}
