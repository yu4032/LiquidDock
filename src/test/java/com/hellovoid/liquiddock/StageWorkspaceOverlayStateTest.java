package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StageWorkspaceOverlayStateTest {

    @Test
    public void rebindingRootInvalidatesOldGenerationWithoutHidingCurrentHost() {
        StageWorkspaceOverlayState state = new StageWorkspaceOverlayState();
        Object root1 = new Object();
        Object workspace1 = new Object();
        long generation1 = state.bind(root1, workspace1);

        assertEquals(StageWorkspaceOverlayState.Action.SHOW,
                state.evaluate(generation1, root1, workspace1,
                        true, true, true, true));

        Object root2 = new Object();
        Object workspace2 = new Object();
        long generation2 = state.bind(root2, workspace2);

        assertEquals(StageWorkspaceOverlayState.Action.IGNORE,
                state.evaluate(generation1, root1, workspace1,
                        true, true, true, false));
        assertEquals(StageWorkspaceOverlayState.Action.SHOW,
                state.evaluate(generation2, root2, workspace2,
                        true, true, true, true));
    }

    @Test
    public void staleOrUnrelatedCellLayoutCannotHideCurrentGeneration() {
        StageWorkspaceOverlayState state = new StageWorkspaceOverlayState();
        Object root = new Object();
        Object workspace = new Object();
        long generation = state.bind(root, workspace);

        assertEquals(StageWorkspaceOverlayState.Action.IGNORE,
                state.evaluate(generation, root, workspace,
                        false, true, true, false));
    }

    @Test
    public void onlyCurrentGenerationOwnsFailClosedVisibility() {
        StageWorkspaceOverlayState state = new StageWorkspaceOverlayState();
        Object root = new Object();
        Object workspace = new Object();
        long generation = state.bind(root, workspace);

        assertEquals(StageWorkspaceOverlayState.Action.HIDE,
                state.evaluate(generation, root, workspace,
                        true, false, true, true));
        assertEquals(StageWorkspaceOverlayState.Action.HIDE,
                state.evaluate(generation, root, workspace,
                        true, true, false, true));
        assertEquals(StageWorkspaceOverlayState.Action.HIDE,
                state.evaluate(generation, root, workspace,
                        true, true, true, false));
    }

    @Test
    public void validGeometryRecoversVisibilityInSameGeneration() {
        StageWorkspaceOverlayState state = new StageWorkspaceOverlayState();
        Object root = new Object();
        Object workspace = new Object();
        long generation = state.bind(root, workspace);

        assertEquals(StageWorkspaceOverlayState.Action.HIDE,
                state.evaluate(generation, root, workspace,
                        true, true, true, false));
        assertEquals(StageWorkspaceOverlayState.Action.SHOW,
                state.evaluate(generation, root, workspace,
                        true, true, true, true));
    }

    @Test
    public void rebindingSameAuthoritiesKeepsGenerationStable() {
        StageWorkspaceOverlayState state = new StageWorkspaceOverlayState();
        Object root = new Object();
        Object workspace = new Object();
        long first = state.bind(root, workspace);
        long second = state.bind(root, workspace);

        assertEquals(first, second);
    }
}
