package com.hellovoid.liquiddock;

/** Pure translation policy. Never adjusts Workspace indicators or vendor layout bounds. */
final class WorkstationAllAppsIndicatorPolicy {
    private WorkstationAllAppsIndicatorPolicy() {}

    /**
     * OEM page-indicator widget. Launcher 4.50 uses ScreenView$SeekBarIndicator,
     * not a class named ScreenIndicator or PageIndicator.
     */
    static boolean isIndicatorClass(String className) {
        return "com.miui.home.launcher.ScreenView$SeekBarIndicator".equals(className)
                || (className != null && (className.contains("ScreenIndicator")
                || className.contains("PageIndicator")));
    }

    static float targetY(float vendorY, boolean workstation, boolean allApps,
                         boolean portrait, int landscapeY, int portraitY) {
        if (!workstation || !allApps) return vendorY;
        return vendorY + (portrait ? portraitY : landscapeY);
    }
}
