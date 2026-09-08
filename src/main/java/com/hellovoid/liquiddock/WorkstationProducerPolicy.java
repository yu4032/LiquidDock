package com.hellovoid.liquiddock;

/** Workspace PassBlur capture policy: HOME stays live while explicit coverage may suspend it. */
final class WorkstationProducerPolicy {
    private WorkstationProducerPolicy() {}

    static boolean shouldPauseSharedProducer(boolean workspaceCovered,
                                             boolean workstationMode) {
        return workspaceCovered && !workstationMode;
    }

    /**
     * A producer that finishes binding after a HOME presentation barrier has already started must
     * inherit that suspension in normal mode. Workstation mode intentionally keeps the historical
     * continuous producer behavior.
     */
    static boolean shouldPauseNewWorkspaceBinding(boolean workspaceCovered,
                                                   boolean presentationPending,
                                                   boolean workstationMode) {
        return (workspaceCovered || presentationPending) && !workstationMode;
    }

    /** Workspace HOME capture is continuous; coverage/presentation suspension is handled separately. */
    static boolean shouldUseSingleFramePulse(boolean workstationMode) {
        return false;
    }

    /** Consuming a live OES frame is not a reason to suspend the continuous Workspace producer. */
    static boolean shouldPauseAfterFrameConsumed(boolean workstationMode) {
        return false;
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
