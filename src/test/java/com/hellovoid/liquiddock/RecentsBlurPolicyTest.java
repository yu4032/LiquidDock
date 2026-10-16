package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RecentsBlurPolicyTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void configuredPercentBecomesRecentsTargetRatio() {
        assertEquals(0f, RecentsBlurPolicy.ratioFromPercent(-20), EPSILON);
        assertEquals(0.4f, RecentsBlurPolicy.ratioFromPercent(40), EPSILON);
        assertEquals(1f, RecentsBlurPolicy.ratioFromPercent(120), EPSILON);
    }

    @Test
    public void gestureProgressKeepsAnimationShapeAndCapsAtConfiguredStrength() {
        assertEquals(0f, RecentsBlurPolicy.scaleGestureRatio(0f, 40), EPSILON);
        assertEquals(0.2f, RecentsBlurPolicy.scaleGestureRatio(0.5f, 40), EPSILON);
        assertEquals(0.4f, RecentsBlurPolicy.scaleGestureRatio(1f, 40), EPSILON);
    }

    @Test
    public void directSurfaceDimAndRecentBlurParamsShareOptOutSemantics() {
        // RecentBlurParams and TaskViewUtils/DimLayer are independent native
        // authorities. On OFF both preserve the exact native value; on ON
        // even a transition-cancellation/return surface-dim update is zero.
        for (float dim : new float[]{0f, 0.05f, 0.35f, 0.8f, 1f}) {
            assertEquals(dim, RecentsBlurPolicy.resolveDimming(dim, false), EPSILON);
            assertEquals(0f, RecentsBlurPolicy.resolveDimming(dim, true), EPSILON);
        }
        // Disabling dim must not collapse the separate wallpaper blur curve.
        assertEquals(.3f, RecentsBlurPolicy.scaleGestureRatio(.5f, 60), EPSILON);
    }

    @Test
    public void wallpaperDimmingCanBeRemovedWithoutChangingVendorValueWhenDisabled() {
        assertEquals(0f, RecentsBlurPolicy.resolveDimming(0.5f, true), EPSILON);
        assertEquals(0f, RecentsBlurPolicy.resolveDimming(1.0f, true), EPSILON);
        assertEquals(0.5f, RecentsBlurPolicy.resolveDimming(0.5f, false), EPSILON);
    }
}
