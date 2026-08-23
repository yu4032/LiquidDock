package com.hellovoid.liquiddock;

/** Pure feature-gate and vendor-owner policy for the SecurityCenter sidebar glass domain. */
final class SidebarGlassPolicy {
    static final String SECURITY_CENTER_PACKAGE = "com.miui.securitycenter";
    static final String MAIN_DOCK_LAYOUT_CLASS =
            "com.miui.gamebooster.windowmanager.newbox.o0";
    static final String ALL_APPS_LAYOUT_CLASS = "com.miui.dock.allapps.w";

    private SidebarGlassPolicy() {}

    static boolean shouldInstall(String packageName, boolean liquidGlassEnabled,
            boolean sidebarGlassEnabled) {
        return SECURITY_CENTER_PACKAGE.equals(packageName)
                && liquidGlassEnabled
                && sidebarGlassEnabled;
    }

    static boolean isVendorMaterialClassName(String className) {
        return MAIN_DOCK_LAYOUT_CLASS.equals(className)
                || ALL_APPS_LAYOUT_CLASS.equals(className);
    }
}
