package com.hellovoid.liquiddock;

/**
 * Cross-process command envelope between Launcher and LiquidDock's Security Center :ui bridge.
 *
 * <p>The actual vendor endpoint remains DockWindowManagerService / ISidebarOverlay.</p>
 */
final class SidebarCommandContract {
    static final String ACTION_PREPARE =
            "com.hellovoid.liquiddock.action.PREPARE_SECURITY_CENTER_SIDEBAR";
    static final String ACTION_SHOW =
            "com.hellovoid.liquiddock.action.SHOW_SECURITY_CENTER_SIDEBAR";
    static final String ACTION_CONFIRM_START =
            "com.hellovoid.liquiddock.action.START_SECURITY_CENTER_SIDEBAR_CONFIRM";
    static final String ACTION_CONFIRM_END =
            "com.hellovoid.liquiddock.action.END_SECURITY_CENTER_SIDEBAR_CONFIRM";
    static final String ACTION_VENDOR_ANIM_STARTED =
            "com.hellovoid.liquiddock.action.SECURITY_CENTER_SIDEBAR_ANIM_STARTED";

    static final String SECURITY_CENTER_PACKAGE = "com.miui.securitycenter";
    static final String SERVICE_CLASS =
            "com.miui.gamebooster.service.DockWindowManagerService";
    static final String SERVICE_ACTION =
            "com.miui.gamebooster.service.DockWindowService";
    static final String SIDEBAR_DESCRIPTOR = "com.miui.sidebar.ISidebarOverlay";
    static final String SIDEBAR_ANIM_CALLBACK_DESCRIPTOR =
            "com.miui.sidebar.ISidebarAnimCallback";
    static final String LAUNCHER_PACKAGE = "com.miui.home";

    static final String EXTRA_X = "x";
    static final String EXTRA_Y = "y";
    static final String EXTRA_WIDTH = "width";
    static final String EXTRA_HEIGHT = "height";
    static final String EXTRA_RADIUS = "radius";
    static final String EXTRA_DESKTOP = "desktop";
    static final String EXTRA_GESTURE_Y = "gesture_y";
    static final String EXTRA_GENERATION = "generation";

    static final int RESULT_READY = 0x5343;
    static final int RESULT_ACCEPTED = 0x5344;
    static final int RESULT_UNAVAILABLE = 0x5345;

    private SidebarCommandContract() {}
}
