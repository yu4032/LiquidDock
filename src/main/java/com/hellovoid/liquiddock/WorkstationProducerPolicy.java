package com.hellovoid.liquiddock;

/** Workspace PassBlur capture policy: ordinary HOME is demand-driven; Workstation stays live. */
final class WorkstationProducerPolicy {
    private WorkstationProducerPolicy() {}

    static boolean shouldPauseSharedProducer(boolean workspaceCovered,
                                             boolean workstationMode) {
        return workspaceCovered && !workstationMode;
    }

    /** A visible Recents capsule owns the shared input even when HOME is frozen.
     * Semantic Recents coverage alone is not a live GPU consumer.
     */
    static boolean keepSharedSourceLiveForRecents(
            boolean visibleConsumer, boolean capsulesEnabled) {
        return visibleConsumer && capsulesEnabled;
    }

    /** Once Recents releases its live lease, ordinary HOME returns to idle capture policy. */
    static boolean shouldPauseAfterRecentsConsumerGone(
            boolean folderFrozen, boolean workstationMode) {
        return folderFrozen || !workstationMode;
    }

    /** Normal Workspace capture is a short freshness pulse; Workstation keeps its live producer. */
    static boolean shouldUseSingleFramePulse(boolean workstationMode) {
        return !workstationMode;
    }

    /** Workstation keeps its shared source active after each consumed frame. */
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
