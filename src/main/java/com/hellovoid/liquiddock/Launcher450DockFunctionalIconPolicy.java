package com.hellovoid.liquiddock;

/** Pure Launcher 4.50 policy for Dock functional-entry glass eligibility. */
final class Launcher450DockFunctionalIconPolicy {
    static final int VIEW_TYPE_SEARCH = 2;
    static final int VIEW_TYPE_ALL_APPS = 512;
    static final int VIEW_TYPE_RECENTS = 1024;
    static final int VIEW_TYPE_HOME = 2048;
    static final int VIEW_TYPE_PHONE = 4096;
    static final int VIEW_TYPE_XIAOAI = 16384;

    private Launcher450DockFunctionalIconPolicy() {}

    static boolean isFunctionalViewType(int viewType) {
        return viewType == VIEW_TYPE_SEARCH
                || viewType == VIEW_TYPE_XIAOAI
                || viewType == VIEW_TYPE_ALL_APPS
                || viewType == VIEW_TYPE_RECENTS
                || viewType == VIEW_TYPE_HOME
                || viewType == VIEW_TYPE_PHONE;
    }

    static boolean shouldRender(
            boolean fullIconGlassEnabled,
            boolean functionalDockGlassEnabled,
            boolean dockDomain,
            boolean functionalEntry) {
        if (fullIconGlassEnabled) return true;
        return functionalDockGlassEnabled && dockDomain && functionalEntry;
    }
}
