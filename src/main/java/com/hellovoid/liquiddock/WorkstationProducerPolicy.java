package com.hellovoid.liquiddock;

/** Workstation Dock and workspace share a root-level PassBlur capture state. */
final class WorkstationProducerPolicy {
    private WorkstationProducerPolicy() {}

    static boolean shouldPauseSharedProducer(boolean workspaceCovered,
                                             boolean workstationMode) {
        return workspaceCovered && !workstationMode;
    }

    static boolean shouldUseSingleFramePulse(boolean workstationMode) {
        return !workstationMode;
    }

    /** Normal HOME can cache its backdrop; workstation Floating Dock must remain live. */
    static boolean shouldKeepDockProducerContinuous(boolean workstationMode) {
        return workstationMode;
    }

    /**
     * Workstation edit mode can resize the root capture surface without replacing its
     * SurfaceControl. PassBlur must be rebound in that case or frame callbacks can stop.
     */
    static boolean shouldRebindForGeometryChange(boolean workstationMode,
                                                  boolean geometryChanged) {
        return workstationMode && geometryChanged;
    }
}
