package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Typed scene-generation contract for Security Center assistant glass ownership. */
public class SecurityCenterGlassSceneStateTest {
    @Test
    public void initialDockRevealsOnlyAfterCurrentGenerationRender() {
        SecurityCenterGlassSceneState state = new SecurityCenterGlassSceneState();
        assertEquals(SecurityCenterGlassSceneState.Scene.DETACHED, state.scene());
        assertEquals(0L, state.generation());

        SecurityCenterGlassSceneState.Decision attached = state.onRootAttached();
        assertTrue(attached.ensureSession);
        assertTrue(attached.invalidateGeneration);
        assertTrue(attached.hideCustom);
        assertFalse(attached.claimCustomOwnership);
        assertFalse(attached.revealCustom);
        assertEquals(1L, attached.generation);
        assertEquals(SecurityCenterGlassSceneState.Scene.PREPARING_DOCK, state.scene());

        SecurityCenterGlassSceneState.Decision settled =
                state.onGeometrySettled(SecurityCenterGlassSceneState.Target.DOCK);
        assertTrue(settled.requestFresh);
        assertEquals(1L, settled.generation);

        SecurityCenterGlassSceneState.Decision stale = state.onFreshFrameRendered(0L);
        assertFalse(stale.claimCustomOwnership);
        assertFalse(stale.revealCustom);
        assertEquals(SecurityCenterGlassSceneState.Scene.PREPARING_DOCK, state.scene());

        SecurityCenterGlassSceneState.Decision fresh = state.onFreshFrameRendered(1L);
        assertTrue(fresh.claimCustomOwnership);
        assertTrue(fresh.revealCustom);
        assertEquals(SecurityCenterGlassSceneState.Scene.DOCK, state.scene());
    }

    @Test
    public void dockAllAppsDockKeepsCustomOwnershipThroughTransitions() {
        SecurityCenterGlassSceneState state = readyDock();

        SecurityCenterGlassSceneState.Decision started = state.onTransitionStarted();
        assertTrue(started.invalidateGeneration);
        assertFalse("transition must not hide an already-authorized custom glass frame",
                started.hideCustom);
        assertFalse("transition must not return material ownership to the vendor",
                started.releaseCustomOwnership);
        assertFalse(started.ensureSession);
        assertFalse(started.shutdownSession);
        assertEquals(2L, started.generation);
        assertEquals(SecurityCenterGlassSceneState.Scene.TRANSITIONING, state.scene());

        SecurityCenterGlassSceneState.Decision appsGeometry =
                state.onGeometrySettled(SecurityCenterGlassSceneState.Target.ALL_APPS);
        assertTrue(appsGeometry.requestFresh);
        assertFalse(appsGeometry.ensureSession);
        assertFalse(appsGeometry.shutdownSession);
        assertEquals(2L, appsGeometry.generation);
        assertEquals(SecurityCenterGlassSceneState.Scene.PREPARING_ALL_APPS, state.scene());

        SecurityCenterGlassSceneState.Decision appsFresh = state.onFreshFrameRendered(2L);
        assertTrue(appsFresh.claimCustomOwnership);
        assertTrue(appsFresh.revealCustom);
        assertEquals(SecurityCenterGlassSceneState.Scene.ALL_APPS, state.scene());

        SecurityCenterGlassSceneState.Decision back = state.onTransitionStarted();
        assertEquals(3L, back.generation);
        assertFalse(back.hideCustom);
        assertFalse(back.releaseCustomOwnership);
        SecurityCenterGlassSceneState.Decision dockGeometry =
                state.onGeometrySettled(SecurityCenterGlassSceneState.Target.DOCK);
        assertTrue(dockGeometry.requestFresh);
        assertEquals(3L, dockGeometry.generation);
        SecurityCenterGlassSceneState.Decision dockFresh = state.onFreshFrameRendered(3L);
        assertTrue(dockFresh.claimCustomOwnership);
        assertTrue(dockFresh.revealCustom);
        assertEquals(SecurityCenterGlassSceneState.Scene.DOCK, state.scene());
    }

    @Test
    public void settledGenerationCannotBeRetargetedByLateDuplicateObserver() {
        SecurityCenterGlassSceneState state = readyDock();
        long generation = state.generation();

        SecurityCenterGlassSceneState.Decision late =
                state.onGeometrySettled(SecurityCenterGlassSceneState.Target.ALL_APPS);

        assertFalse(late.requestFresh);
        assertEquals(generation, state.generation());
        assertEquals(SecurityCenterGlassSceneState.Scene.DOCK, state.scene());
    }

    @Test
    public void staleTransitionGenerationCannotSettleANewerTransition() {
        SecurityCenterGlassSceneState state = readyDock();
        SecurityCenterGlassSceneState.Decision first = state.onTransitionStarted();
        long staleGeneration = first.generation;
        state.onGeometrySettled(SecurityCenterGlassSceneState.Target.ALL_APPS, staleGeneration);
        state.onFreshFrameRendered(staleGeneration);
        assertEquals(SecurityCenterGlassSceneState.Scene.ALL_APPS, state.scene());

        SecurityCenterGlassSceneState.Decision second = state.onTransitionStarted();
        assertTrue(second.generation > staleGeneration);
        SecurityCenterGlassSceneState.Decision stale =
                state.onGeometrySettled(SecurityCenterGlassSceneState.Target.DOCK, staleGeneration);

        assertFalse(stale.requestFresh);
        assertEquals(SecurityCenterGlassSceneState.Scene.TRANSITIONING, state.scene());
    }

    @Test
    public void disableFailureAndDetachFailClosedAndRejectStaleReveal() {
        SecurityCenterGlassSceneState disabledState = readyDock();
        long oldGeneration = disabledState.generation();
        SecurityCenterGlassSceneState.Decision disabled = disabledState.onRuntimeDisabled();
        assertTrue(disabled.hideCustom);
        assertTrue(disabled.releaseCustomOwnership);
        assertTrue(disabled.shutdownSession);
        assertTrue(disabled.generation > oldGeneration);
        assertEquals(SecurityCenterGlassSceneState.Scene.DETACHED, disabledState.scene());
        SecurityCenterGlassSceneState.Decision disabledStale =
                disabledState.onFreshFrameRendered(oldGeneration);
        assertFalse(disabledStale.claimCustomOwnership);
        assertFalse(disabledStale.revealCustom);

        SecurityCenterGlassSceneState failedState = readyDock();
        SecurityCenterGlassSceneState.Decision failed = failedState.onTerminalFailure();
        assertTrue(failed.hideCustom);
        assertTrue(failed.releaseCustomOwnership);
        assertTrue(failed.shutdownSession);
        assertEquals(SecurityCenterGlassSceneState.Scene.DETACHED, failedState.scene());

        SecurityCenterGlassSceneState detachedState = readyDock();
        SecurityCenterGlassSceneState.Decision detached = detachedState.onRootDetached();
        assertTrue(detached.hideCustom);
        assertTrue(detached.releaseCustomOwnership);
        assertTrue(detached.shutdownSession);
        assertEquals(SecurityCenterGlassSceneState.Scene.DETACHED, detachedState.scene());
    }

    private static SecurityCenterGlassSceneState readyDock() {
        SecurityCenterGlassSceneState state = new SecurityCenterGlassSceneState();
        state.onRootAttached();
        state.onGeometrySettled(SecurityCenterGlassSceneState.Target.DOCK);
        state.onFreshFrameRendered(state.generation());
        return state;
    }
}
