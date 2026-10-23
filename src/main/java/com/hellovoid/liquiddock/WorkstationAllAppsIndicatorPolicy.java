package com.hellovoid.liquiddock;

/** Pure translation policy. Never adjusts Workspace indicators or vendor layout bounds. */
final class WorkstationAllAppsIndicatorPolicy {
    private WorkstationAllAppsIndicatorPolicy() {}

    static float targetY(float vendorY, boolean workstation, boolean allApps,
                         boolean portrait, int landscapeY, int portraitY) {
        if (!workstation || !allApps) return vendorY;
        return vendorY + (portrait ? portraitY : landscapeY);
    }
}
