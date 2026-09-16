package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Runtime contract for reusing one full-root backdrop while floating Gboard moves. */
public class GboardFloatingFullscreenBackdropDragStateTest {
    @Test public void dragFreezesBackdropButKeepsGeometryRenderingLive() {
        GboardFloatingFullscreenBackdropDragState state =
                new GboardFloatingFullscreenBackdropDragState(1L);

        state.onBackdropPrepared(1L);
        state.onOutputPresented();

        assertTrue(state.beginDrag());
        assertEquals(GboardFloatingFullscreenBackdropDragState.Mode.DRAG, state.mode());
        assertTrue(state.shouldPauseSourceUpdates());
        assertFalse(state.shouldPrepareBackdrop(1L));
        assertTrue(state.shouldRenderGeometry());
        assertEquals(1L, state.backdropGeneration());

        for (int i = 0; i < 100; i++) {
            assertTrue(state.shouldRenderGeometry());
            assertFalse(state.shouldPrepareBackdrop(1L));
        }
        assertEquals(1L, state.backdropGeneration());
    }

    @Test public void dragCannotFreezeBeforeAFullBackdropWasPresented() {
        GboardFloatingFullscreenBackdropDragState state =
                new GboardFloatingFullscreenBackdropDragState(1L);

        state.onBackdropPrepared(1L);

        assertFalse(state.beginDrag());
        assertEquals(GboardFloatingFullscreenBackdropDragState.Mode.LIVE, state.mode());
        assertFalse(state.shouldPauseSourceUpdates());
    }

    @Test public void releaseRequestsOneNewGenerationAndKeepsOldBackdropUntilFreshArrives() {
        GboardFloatingFullscreenBackdropDragState state =
                new GboardFloatingFullscreenBackdropDragState(1L);
        state.onBackdropPrepared(1L);
        state.onOutputPresented();
        assertTrue(state.beginDrag());

        GboardFloatingFullscreenBackdropDragState.Recovery recovery = state.endDrag();
        assertTrue(recovery.resumeSourceUpdates);
        assertTrue(recovery.reconcileRoot);
        assertTrue(recovery.requestFresh);
        assertEquals(2L, recovery.generation);
        assertEquals(GboardFloatingFullscreenBackdropDragState.Mode.RECOVERING, state.mode());
        assertTrue(state.shouldRenderGeometry());
        assertEquals(1L, state.backdropGeneration());
        assertFalse(state.shouldPrepareBackdrop(1L));
        assertTrue(state.shouldPrepareBackdrop(2L));

        GboardFloatingFullscreenBackdropDragState.Recovery duplicate = state.endDrag();
        assertFalse(duplicate.resumeSourceUpdates);
        assertFalse(duplicate.reconcileRoot);
        assertFalse(duplicate.requestFresh);
        assertEquals(2L, duplicate.generation);
    }

    @Test public void recoveryReturnsLiveOnlyAfterFreshBackdropIsPresented() {
        GboardFloatingFullscreenBackdropDragState state =
                new GboardFloatingFullscreenBackdropDragState(1L);
        state.onBackdropPrepared(1L);
        state.onOutputPresented();
        assertTrue(state.beginDrag());
        GboardFloatingFullscreenBackdropDragState.Recovery recovery = state.endDrag();

        assertTrue(state.shouldPrepareBackdrop(recovery.generation));
        state.onBackdropPrepared(recovery.generation);
        assertEquals(recovery.generation, state.backdropGeneration());
        assertEquals(GboardFloatingFullscreenBackdropDragState.Mode.RECOVERING, state.mode());

        state.onOutputPresented();
        assertEquals(GboardFloatingFullscreenBackdropDragState.Mode.LIVE, state.mode());
        assertFalse(state.shouldPauseSourceUpdates());
    }
}
