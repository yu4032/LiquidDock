package com.hellovoid.liquiddock;

/** Pure feature-gate policy for the SecurityCenter sidebar liquid-glass domain. */
final class SidebarGlassPolicy {
    static final String SECURITY_CENTER_PACKAGE = "com.miui.securitycenter";

    private SidebarGlassPolicy() {}

    static boolean shouldInstall(String packageName, boolean liquidGlassEnabled,
            boolean sidebarGlassEnabled) {
        return SECURITY_CENTER_PACKAGE.equals(packageName)
                && liquidGlassEnabled
                && sidebarGlassEnabled;
    }
}
