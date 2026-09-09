package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure cache-ownership contract: source freshness and consumer presentation are separate clocks. */
public class LauncherGlassBackdropRetentionPolicyTest {
    @Test
    public void sceneFreshnessInvalidationKeepsPreparedBackdropRenderable() {
        assertTrue(LauncherGlassBackdropRetentionPolicy.preservesPreparedBackdrop(
                LauncherGlassBackdropRetentionPolicy.Invalidation.SCENE_FRESHNESS));
    }

    @Test
    public void producerEndpointRolloverKeepsPreparedBackdropRenderable() {
        assertTrue(LauncherGlassBackdropRetentionPolicy.preservesPreparedBackdrop(
                LauncherGlassBackdropRetentionPolicy.Invalidation.SOURCE_ENDPOINT));
    }

    @Test
    public void renderDomainChangeDropsPreparedBackdrop() {
        assertFalse(LauncherGlassBackdropRetentionPolicy.preservesPreparedBackdrop(
                LauncherGlassBackdropRetentionPolicy.Invalidation.RENDER_DOMAIN));
    }

    @Test
    public void renderTargetDestructionDropsPreparedBackdrop() {
        assertFalse(LauncherGlassBackdropRetentionPolicy.preservesPreparedBackdrop(
                LauncherGlassBackdropRetentionPolicy.Invalidation.RENDER_TARGET));
    }

    @Test
    public void staleCachedGenerationStillRequiresFreshSourceFrame() {
        assertTrue(PassBlurQualityPolicy.requiresFreshConsumerFrame(7L, 8L));
        assertFalse(PassBlurQualityPolicy.requiresFreshConsumerFrame(8L, 8L));
    }
}
