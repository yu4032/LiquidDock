package com.hellovoid.liquiddock;

/** Small package-local bridge for the dedicated drag session's live producer lifecycle. */
final class LauncherLiveDragSessionBridge {
    private static final String TAG = "[DC][DragGlass]";

    private LauncherLiveDragSessionBridge() {}

    static boolean ensureLive(LauncherGlassSession session) {
        if (session == null || session.isShutdown()) return false;
        try {
            Object backendValue = HookUtil.getField(session, "sourceBackend");
            Object generationValue = HookUtil.getField(session, "sceneGeneration");
            if (!(backendValue instanceof RootPassBlurBackend)
                    || !(generationValue instanceof Number)) return false;
            RootPassBlurBackend backend = (RootPassBlurBackend) backendValue;
            long generation = ((Number) generationValue).longValue();
            backend.setUpdatesEnabled(true, "launcher-drag-live");
            if (!hasPreparedBackdrop(session)) session.requestFreshBackdrop(generation);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " live source activation failed: " + error);
            return false;
        }
    }

    static boolean hasPreparedBackdrop(LauncherGlassSession session) {
        if (session == null || session.isShutdown()) return false;
        try {
            Object value = HookUtil.getField(session, "backdropPrepared");
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
