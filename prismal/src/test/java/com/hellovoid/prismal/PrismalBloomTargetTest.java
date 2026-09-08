package com.hellovoid.prismal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PrismalBloomTargetTest {
    @Test
    public void addsTwoSidedBloomAndThreeSigmaBlurPadding() {
        PrismalBloomTarget.Spec spec = PrismalBloomTarget.plan(
                200f, 80f, 20f, 5f, 1000, 600);

        // padding = bloom half-width 10 + 3*sigma 15 + 2px guard = 27px.
        assertEquals(27f, spec.paddingPx, 0.0001f);
        assertEquals(254, spec.width);
        assertEquals(134, spec.height);
    }

    @Test
    public void clampsLocalTargetToLogicalOutputInsteadOfAllocatingBeyondIt() {
        PrismalBloomTarget.Spec spec = PrismalBloomTarget.plan(
                980f, 580f, 40f, 12f, 1000, 600);

        assertEquals(1000, spec.width);
        assertEquals(600, spec.height);
        assertTrue(spec.paddingPx > 0f);
    }

    @Test
    public void sanitizesNegativeAndNonFiniteControls() {
        PrismalBloomTarget.Spec spec = PrismalBloomTarget.plan(
                Float.NaN, -30f, Float.POSITIVE_INFINITY, -4f, 320, 240);

        assertEquals(2f, spec.paddingPx, 0.0001f);
        assertEquals(5, spec.width);
        assertEquals(5, spec.height);
    }

    @Test
    public void largerBloomAndBlurMonotonicallyIncreaseLocalTargetUntilClamped() {
        PrismalBloomTarget.Spec small = PrismalBloomTarget.plan(
                120f, 50f, 8f, 2f, 500, 300);
        PrismalBloomTarget.Spec large = PrismalBloomTarget.plan(
                120f, 50f, 24f, 8f, 500, 300);

        assertTrue(large.paddingPx > small.paddingPx);
        assertTrue(large.width > small.width);
        assertTrue(large.height > small.height);
    }

    @Test
    public void outputDimensionsAreAlwaysAtLeastOne() {
        PrismalBloomTarget.Spec spec = PrismalBloomTarget.plan(
                0f, 0f, 0f, 0f, 0, 0);

        assertEquals(1, spec.width);
        assertEquals(1, spec.height);
    }
}
