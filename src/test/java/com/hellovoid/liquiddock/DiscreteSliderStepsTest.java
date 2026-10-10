package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Behavioral checks for integer row/column stops, not just source-text assertions. */
public class DiscreteSliderStepsTest {
    @Test
    public void gridColumnsHaveEveryIntegerStopFromTwoThroughTen() {
        int steps = DiscreteSliderSteps.forIntegerRange(2, 10);
        assertEquals(7, steps);
        for (int value = 2; value <= 10; value++) {
            assertEquals(value, DiscreteSliderSteps.snap(value + 0.30f, 2f, 10f, steps), 0f);
            assertEquals(value, DiscreteSliderSteps.snap(value - 0.30f, 2f, 10f, steps), 0f);
        }
        assertEquals(2f, DiscreteSliderSteps.snap(-100f, 2f, 10f, steps), 0f);
        assertEquals(10f, DiscreteSliderSteps.snap(100f, 2f, 10f, steps), 0f);
        assertEquals(4f, DiscreteSliderSteps.snap(3.5f, 2f, 10f, steps), 0f);
    }

    @Test
    public void gridRowsHaveEveryIntegerStopFromTwoThroughSix() {
        int steps = DiscreteSliderSteps.forIntegerRange(2, 6);
        assertEquals(3, steps);
        for (int value = 2; value <= 6; value++) {
            assertEquals(value, DiscreteSliderSteps.snap(value + 0.3f, 2f, 6f, steps), 0f);
        }
    }

    @Test
    public void unrelatedUnsteppedSliderRemainsContinuous() {
        assertEquals(2.35f, DiscreteSliderSteps.snap(2.35f, 2f, 10f, 0), 0.00001f);
        assertEquals(2.35f, DiscreteSliderSteps.snap(2.35f, 2f, 10f, -1), 0.00001f);
    }

    @Test
    public void compactControlsHaveVisibleIntegerOrTenthTicks() {
        assertEquals(7, DiscreteSliderSteps.forStoragePrecision(2, 10, false));
        assertEquals(3, DiscreteSliderSteps.forStoragePrecision(2, 6, false));
        assertEquals(19, DiscreteSliderSteps.forStoragePrecision(0, 2, true));
        assertEquals(0, DiscreteSliderSteps.forStoragePrecision(0, 2000, false));
        assertEquals(0, DiscreteSliderSteps.forStoragePrecision(-600, 600, false));
        assertEquals(0, DiscreteSliderSteps.forStoragePrecision(-50, 50, true));
    }

    @Test
    public void wideIntegerSlidersSnapWithoutThousandsOfNativeTicks() {
        assertEquals(251f, DiscreteSliderSteps.snap(250.6f, 0f, 2000f, 0, 1f), 0f);
        assertEquals(-359f, DiscreteSliderSteps.snap(-359.49f, -600f, 600f, 0, 1f), 0f);
        assertEquals(-360f, DiscreteSliderSteps.snap(-359.51f, -600f, 600f, 0, 1f), 0f);
        assertEquals(2000f, DiscreteSliderSteps.snap(2099.5f, 0f, 2000f, 0, 1f), 0f);
    }

    @Test
    public void decimalStorageSlidersSnapPreciselyToTenths() {
        assertEquals(1.2f, DiscreteSliderSteps.snap(1.24f, 0f, 12f, 0, 0.1f), 0.00001f);
        assertEquals(1.3f, DiscreteSliderSteps.snap(1.26f, 0f, 12f, 0, 0.1f), 0.00001f);
        assertEquals(-2.7f, DiscreteSliderSteps.snap(-2.66f, -10f, 10f, 0, 0.1f), 0.00001f);
        assertEquals(0f, DiscreteSliderSteps.snap(-8f, 0f, 12f, 0, 0.1f), 0f);
        assertEquals(12f, DiscreteSliderSteps.snap(13f, 0f, 12f, 0, 0.1f), 0f);
    }

    @Test
    public void explicitGridStepsRemainCompatible() {
        assertEquals(7f, DiscreteSliderSteps.snap(7.3f, 2f, 10f, 7, 0.1f), 0f);
        assertEquals(4f, DiscreteSliderSteps.snap(3.7f, 2f, 6f, 3, 1f), 0f);
        assertEquals(5.75f, DiscreteSliderSteps.snap(5.75f, 2f, 10f, 0, 0f), 0f);
    }
}
