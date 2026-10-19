package com.hellovoid.liquiddock;

/** Replaces only the Workstation Dock capsule, never its icons or normal Dock. */
final class WorkstationTaskbarPolicy {
    private WorkstationTaskbarPolicy() {}

    static boolean shouldReplace(boolean workstation, boolean requested,
            boolean panelAttached) {
        return workstation && requested && panelAttached;
    }

    static boolean shouldHideVendorMaterial(boolean workstation, boolean requested,
            boolean panelAttached, boolean liquidGlassEnabled) {
        return shouldReplace(workstation, requested, panelAttached) && !liquidGlassEnabled;
    }
}
