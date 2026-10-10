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
}
