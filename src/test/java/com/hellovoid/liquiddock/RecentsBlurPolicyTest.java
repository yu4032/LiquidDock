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
}
