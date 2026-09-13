package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StageWorkspaceOverlayContentPolicyTest {

    @Test
    public void onlyCurrentShowActionMayPullRecentsSnapshot() {
        assertTrue(StageWorkspaceOverlayContentPolicy.shouldRefresh(
                StageWorkspaceOverlayState.Action.SHOW));
        assertFalse(StageWorkspaceOverlayContentPolicy.shouldRefresh(
                StageWorkspaceOverlayState.Action.HIDE));
        assertFalse(StageWorkspaceOverlayContentPolicy.shouldRefresh(
                StageWorkspaceOverlayState.Action.IGNORE));
        assertFalse(StageWorkspaceOverlayContentPolicy.shouldRefresh(null));
    }

    @Test
    public void hiddenOrIgnoredActionsClearPresentationWithoutSourcePull() {
        assertTrue(StageWorkspaceOverlayContentPolicy.shouldClear(
                StageWorkspaceOverlayState.Action.HIDE));
        assertFalse(StageWorkspaceOverlayContentPolicy.shouldClear(
                StageWorkspaceOverlayState.Action.IGNORE));
        assertFalse(StageWorkspaceOverlayContentPolicy.shouldClear(
                StageWorkspaceOverlayState.Action.SHOW));
        assertFalse(StageWorkspaceOverlayContentPolicy.shouldClear(null));
    }
}
