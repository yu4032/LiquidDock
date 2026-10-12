package com.hellovoid.liquiddock;

/**
 * Lifecycle eligibility for one shared Launcher-root GPU producer.
 *
 * App coverage does not imply Recents coverage: Recents capsules can continue
 * consuming the same OES producer while the HOME TextureView is occluded.
 * Workstation mode retains its independent producer/rendering authority.
 */
final class WorkspaceRenderEligibilityPolicy {
    private WorkspaceRenderEligibilityPolicy() {}

    static boolean renderHome(boolean launcherStopped, boolean workstationMode) {
        return !launcherStopped || workstationMode;
    }

    static boolean normalizeInput(boolean launcherStopped, boolean workstationMode,
                                  boolean visibleRecentsConsumer) {
        return renderHome(launcherStopped, workstationMode) || visibleRecentsConsumer;
    }

    static boolean pauseProducerForAppCover(boolean launcherStopped,
                                             boolean workstationMode,
                                             boolean visibleRecentsConsumer) {
        return launcherStopped && !normalizeInput(launcherStopped, workstationMode,
                visibleRecentsConsumer);
    }
}
