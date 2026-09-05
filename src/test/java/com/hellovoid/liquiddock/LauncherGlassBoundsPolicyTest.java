package com.hellovoid.liquiddock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Symmetric component size offset policy. */
public class LauncherGlassBoundsPolicyTest {
    @Test
    public void positiveOffsetExpandsEveryEdge() {
        assertArrayEquals(new float[]{-12f, -12f, 112f, 212f},
                LauncherGlassBoundsPolicy.apply(0f, 0f, 100f, 200f, 12f), 0.0001f);
    }

    @Test
    public void negativeOffsetInsetsEveryEdge() {
        assertArrayEquals(new float[]{20f, 20f, 80f, 180f},
                LauncherGlassBoundsPolicy.apply(0f, 0f, 100f, 200f, -20f), 0.0001f);
    }

    @Test
    public void oversizedInsetStillProducesPositiveBounds() {
        float[] bounds = LauncherGlassBoundsPolicy.apply(0f, 0f, 20f, 20f, -50f);
        assertTrue(bounds[2] > bounds[0]);
        assertTrue(bounds[3] > bounds[1]);
    }
}
