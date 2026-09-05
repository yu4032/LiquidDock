package com.hellovoid.liquiddock;

/** Android-free decisions for Dock native-shadow ownership during runtime and resize animation. */
final class DockShadowRuntimePolicy {
    enum GeometrySync { REMEMBER_ONLY, SYNC_CONFIG }

    private DockShadowRuntimePolicy() {}

    static GeometrySync geometrySync(boolean workstationMode, boolean animationActive) {
        return workstationMode || animationActive
                ? GeometrySync.REMEMBER_ONLY
                : GeometrySync.SYNC_CONFIG;
    }

    static boolean shouldApplyTemporaryOverrides(boolean workstationMode,
                                                 boolean dockCustomizationEnabled,
                                                 boolean hasConfig) {
        return !workstationMode && dockCustomizationEnabled && hasConfig;
    }

    static boolean shouldRefreshVendorShadow(boolean workstationMode,
                                             boolean dockCustomizationEnabled) {
        return !workstationMode && dockCustomizationEnabled;
    }
}
