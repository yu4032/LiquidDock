package com.hellovoid.liquiddock;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
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

    @Test
    public void allocationFreeScratchMatchesLegacyAndPreservesTail() {
        // Include exact collapse bounds, reversed input and non-finite offsets.
        float[][] cases = {
                {0f, 0f, 100f, 200f, 12f},
                {0f, 0f, 100f, 200f, -20f},
                {0f, 0f, 20f, 20f, -50f},
                {100f, 200f, 0f, 50f, 0f},
                {-10.25f, 3.5f, 4.75f, 22f, Float.NaN},
                {-10.25f, 3.5f, 4.75f, 22f, Float.POSITIVE_INFINITY},
                {-10.25f, 3.5f, 4.75f, 22f, Float.NEGATIVE_INFINITY},
        };
        float[] reusable = new float[8];
        for (float[] input : cases) {
            java.util.Arrays.fill(reusable, 1234.25f);
            LauncherGlassBoundsPolicy.applyInto(
                    input[0], input[1], input[2], input[3], input[4], reusable);
            float[] legacy = LauncherGlassBoundsPolicy.apply(
                    input[0], input[1], input[2], input[3], input[4]);
            assertArrayEquals(legacy, java.util.Arrays.copyOf(reusable, 4), 0f);
            for (int i = 4; i < reusable.length; i++) {
                assertTrue("unused scratch slots must remain untouched",
                        Float.compare(1234.25f, reusable[i]) == 0);
            }
        }
    }

    @Test
    public void oversizedInsetAndNonFiniteOffsetHavePinnedExactGeometry() {
        float[] scratch = new float[8];
        LauncherGlassBoundsPolicy.applyInto(0f, 0f, 20f, 20f, -50f, scratch);
        assertArrayEquals(new float[]{9.5f, 9.5f, 10.5f, 10.5f},
                java.util.Arrays.copyOf(scratch, 4), 0f);

        // Non-finite offsets must act as zero, preserving the original policy.
        LauncherGlassBoundsPolicy.applyInto(0f, 0f, 100f, 200f,
                Float.POSITIVE_INFINITY, scratch);
        assertArrayEquals(new float[]{0f, 0f, 100f, 200f},
                java.util.Arrays.copyOf(scratch, 4), 0f);
        LauncherGlassBoundsPolicy.applyInto(0f, 0f, 100f, 200f, Float.NaN, scratch);
        assertArrayEquals(new float[]{0f, 0f, 100f, 200f},
                java.util.Arrays.copyOf(scratch, 4), 0f);
    }

    @Test
    public void undersizedScratchFailsBeforeWritingAnyBounds() {
        float[] scratch = {42f, 24f, 5f};
        assertThrows(IllegalArgumentException.class,
                () -> LauncherGlassBoundsPolicy.applyInto(
                        0f, 0f, 100f, 100f, 5f, scratch));
        assertArrayEquals(new float[]{42f, 24f, 5f}, scratch, 0f);
        assertThrows(IllegalArgumentException.class,
                () -> LauncherGlassBoundsPolicy.applyInto(
                        0f, 0f, 100f, 100f, 5f, null));
    }

}
