package com.hellovoid.liquiddock;

/** Pure policy: a superseded Workspace native layer must not block its replacement. */
final class WorkspacePassBlurRolloverPolicy {
    private WorkspacePassBlurRolloverPolicy() {}

    static boolean distinctNativeLayer(int oldLayerId, int nextLayerId) {
        return oldLayerId >= 0 && nextLayerId >= 0 && oldLayerId != nextLayerId;
    }

    /**
     * Isolate without freeing the old producer when a verified replacement exists.
     * A distinct SurfaceFlinger layer is independent even when the stale Java
     * SurfaceControl remains valid; same-layer geometry changes must await detach.
     */
    static boolean isolate(boolean workspace, boolean oldRootValid,
            boolean replacementValid, boolean sameGeneration, boolean distinctLayer) {
        return workspace && replacementValid && !sameGeneration
                && (!oldRootValid || distinctLayer);
    }
}
