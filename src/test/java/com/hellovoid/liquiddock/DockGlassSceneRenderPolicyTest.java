package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DockGlassSceneRenderPolicyTest {
    @Test
    public void sceneOnlyChangeRendersFromFreshProducerFrame() {
        assertTrue(DockGlassSceneRenderPolicy.shouldRenderSceneOnlyChange(true, true));
    }

    @Test
    public void stableSceneDoesNotCreateIdleRendering() {
        assertFalse(DockGlassSceneRenderPolicy.shouldRenderSceneOnlyChange(false, true));
    }

    @Test
    public void sceneChangeWaitsForAValidProducerFrame() {
        assertFalse(DockGlassSceneRenderPolicy.shouldRenderSceneOnlyChange(true, false));
    }
}
