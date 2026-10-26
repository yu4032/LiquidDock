package com.hellovoid.liquiddock;

/** Pure policy: a destroyed old workspace ViewRoot must not block its replacement. */
final class WorkspacePassBlurRolloverPolicy {
    private WorkspacePassBlurRolloverPolicy() {}

    /**
     * Isolate (do not destroy) the old GPU producer only when there is a distinct,
     * verified live replacement root and the old native surface is already invalid.
     * Ordinary geometry changes and a still-live source require committed detach.
     */
    static boolean isolate(
            boolean workspace, boolean oldRootValid,
            boolean replacementValid, boolean sameGeneration) {
        return workspace && !oldRootValid && replacementValid && !sameGeneration;
    }
}
