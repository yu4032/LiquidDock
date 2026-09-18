package com.hellovoid.liquiddock;

final class SidebarCommandContract {
    static final String ACTION_PREPARE =
            "com.hellovoid.liquiddock.action.PREPARE_SECURITY_CENTER_SIDEBAR";
    static final String ACTION_SHOW =
            "com.hellovoid.liquiddock.action.SHOW_SECURITY_CENTER_SIDEBAR";
    static final String SECURITY_CENTER_PACKAGE = "com.miui.securitycenter";
    static final String SERVICE_CLASS =
            "com.miui.gamebooster.service.DockWindowManagerService";
    static final String SERVICE_ACTION =
            "com.miui.gamebooster.service.DockWindowService";
    static final String SIDEBAR_DESCRIPTOR = "com.miui.sidebar.ISidebarOverlay";

    static final String EXTRA_X = "x";
    static final String EXTRA_Y = "y";
    static final String EXTRA_WIDTH = "width";
    static final String EXTRA_HEIGHT = "height";
    static final String EXTRA_RADIUS = "radius";

    static final int RESULT_READY = 0x5343;
    static final int RESULT_SHOWN = 0x5344;
    static final int RESULT_UNAVAILABLE = 0x5345;

    private SidebarCommandContract() {}
}
