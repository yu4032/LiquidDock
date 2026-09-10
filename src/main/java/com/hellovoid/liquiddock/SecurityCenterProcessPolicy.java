package com.hellovoid.liquiddock;

/** Exact process gate for Security Center hooks. */
final class SecurityCenterProcessPolicy {
    static final String PACKAGE = "com.miui.securitycenter";
    static final String UI_PROCESS = "com.miui.securitycenter:ui";

    private SecurityCenterProcessPolicy() {}

    static boolean shouldInstall(String packageName, String processName) {
        return PACKAGE.equals(packageName) && UI_PROCESS.equals(processName);
    }
}
