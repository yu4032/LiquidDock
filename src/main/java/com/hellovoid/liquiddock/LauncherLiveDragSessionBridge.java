package com.hellovoid.liquiddock;

/** Small package-local bridge for the dedicated drag session's live producer lifecycle. */
final class LauncherLiveDragSessionBridge {
    private LauncherLiveDragSessionBridge() {}

    static boolean ensureLive(LauncherGlassSession session) {
        return session != null && session.ensureLiveDragSource();
    }

    static boolean hasPreparedBackdrop(LauncherGlassSession session) {
        return session != null && session.hasPreparedBackdrop();
    }
}
