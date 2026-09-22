package com.hellovoid.liquiddock;

/** Tiny typed guard that makes the Launcher feature installation order regression-testable. */
final class LauncherInstallSequence {
    enum Stage {
        WORKSTATION_RUNTIME,
        CONFIG_LOADED,
        DOCK_FOUNDATION,
        GRID,
        DOCK_SHADOW,
        GLASS_DECISION,
        FALLBACK_DOCK
    }

    private int next;

    void advance(Stage stage) {
        Stage[] stages = Stage.values();
        if (next >= stages.length || stages[next] != stage) {
            Stage expected = next < stages.length ? stages[next] : null;
            throw new IllegalStateException(
                    "Launcher install order expected=" + expected + " actual=" + stage);
        }
        next++;
    }

    void finishAfterConfigWhenDisabled() {
        if (next != Stage.DOCK_FOUNDATION.ordinal()) {
            throw new IllegalStateException("disabled composition terminated before config");
        }
    }

    void finishAtGlassOwner() {
        if (next != Stage.FALLBACK_DOCK.ordinal()) {
            throw new IllegalStateException("glass owner returned before glass decision");
        }
    }

    void finishFallback() {
        if (next != Stage.values().length) {
            throw new IllegalStateException("fallback composition incomplete");
        }
    }
}
