package com.hellovoid.liquiddock;

/** Pure generation/visibility authority for the Stage overlay runtime. */
final class StageWorkspaceOverlayState {
    enum Action {
        IGNORE,
        HIDE,
        SHOW
    }

    private Object root;
    private Object workspace;
    private long generation;

    long bind(Object nextRoot, Object nextWorkspace) {
        if (nextRoot == null || nextWorkspace == null) {
            throw new IllegalArgumentException("overlay authorities must be non-null");
        }
        if (root == nextRoot && workspace == nextWorkspace) return generation;
        root = nextRoot;
        workspace = nextWorkspace;
        generation++;
        return generation;
    }

    Action evaluate(long candidateGeneration,
                    Object candidateRoot,
                    Object candidateWorkspace,
                    boolean cellBelongsWorkspace,
                    boolean stageActive,
                    boolean landscapeHome,
                    boolean geometryValid) {
        if (candidateGeneration != generation
                || candidateRoot != root
                || candidateWorkspace != workspace
                || !cellBelongsWorkspace) {
            return Action.IGNORE;
        }
        if (!stageActive || !landscapeHome || !geometryValid) return Action.HIDE;
        return Action.SHOW;
    }
}
