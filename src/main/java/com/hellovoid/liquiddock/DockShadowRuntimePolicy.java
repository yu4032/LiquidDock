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

    /** Native shadow is replaced only while a real GPU Dock glass host is active. */
    static boolean shouldSuppressNativeForPrismal(
            boolean glassEnabled, boolean hostBound, boolean zeroCopyActive) {
        return glassEnabled && hostBound && zeroCopyActive;
    }

    static boolean shouldRefreshVendorShadow(boolean workstationMode,
                                             boolean dockCustomizationEnabled) {
        return !workstationMode && dockCustomizationEnabled;
    }
}
