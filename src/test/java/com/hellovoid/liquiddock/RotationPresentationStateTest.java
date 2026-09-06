package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

/** Rotation owns a hard presentation gate until the matching fresh frame is rendered. */
public class RotationPresentationStateTest {
    private static boolean beginRotationPresentation(
            LauncherGlassSceneController.StateMachine state) {
        try {
            Method method = state.getClass().getDeclaredMethod("onRotationStarted");
            method.setAccessible(true);
            method.invoke(state);
            return true;
        } catch (ReflectiveOperationException missingRotationGate) {
            return false;
        }
    }

    @Test
    public void rotationStartImmediatelyHidesCachedLayer() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        state.consumeFadeReveal();
        assertTrue(state.isLayerVisible());

        assertTrue("rotation must have an explicit presentation gate",
                beginRotationPresentation(state));
        assertFalse("old-orientation cached pixels must not survive into resized TextureView",
                state.isLayerVisible());
    }

    @Test
    public void rotationRemainsHiddenUntilMatchingFreshGeneration() {
        LauncherGlassSceneController.StateMachine state =
                new LauncherGlassSceneController.StateMachine();
        state.onRootReady();
        state.onFreshFrameReady(state.generation());
        state.consumeFadeReveal();
        long staleGeneration = state.generation();

        assertTrue(beginRotationPresentation(state));
        long rotationGeneration = state.generation();
        assertTrue(rotationGeneration > staleGeneration);
        assertFalse(state.isLayerVisible());

        state.onFreshFrameReady(staleGeneration);
        assertFalse("stale orientation frame must not release rotation presentation gate",
                state.isLayerVisible());

        state.onFreshFrameReady(rotationGeneration);
        assertTrue("matching fresh orientation frame may restore presentation",
                state.isLayerVisible());
        assertFalse("rotation recovery must not add a synthetic reveal animation",
                state.consumeFadeReveal());
    }
}
