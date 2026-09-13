package com.hellovoid.liquiddock;

/** Pure visibility/content authority for Stage overlay snapshot refreshes. */
final class StageWorkspaceOverlayContentPolicy {
    private StageWorkspaceOverlayContentPolicy() {}

    static boolean shouldRefresh(StageWorkspaceOverlayState.Action action) {
        return action == StageWorkspaceOverlayState.Action.SHOW;
    }

    static boolean shouldClear(StageWorkspaceOverlayState.Action action) {
        return action == StageWorkspaceOverlayState.Action.HIDE;
    }
}
