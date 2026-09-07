package com.hellovoid.liquiddock;

/** Workspace PassBlur capture policy: HOME stays live while explicit coverage may suspend it. */
final class WorkstationProducerPolicy {
    private WorkstationProducerPolicy() {}

    static boolean shouldPauseSharedProducer(boolean workspaceCovered,
                                             boolean workstationMode) {
        return workspaceCovered && !workstationMode;
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
