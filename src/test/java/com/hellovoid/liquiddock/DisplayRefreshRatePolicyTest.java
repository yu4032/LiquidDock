package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DisplayRefreshRatePolicyTest {
    @Test
    public void roundsPanelRatesForIntegerFpsUi() {
        assertEquals(60, DisplayRefreshRatePolicy.roundRefreshRate(59.94f));
        assertEquals(120, DisplayRefreshRatePolicy.roundRefreshRate(120.0f));
        assertEquals(144, DisplayRefreshRatePolicy.roundRefreshRate(143.98f));
        assertEquals(165, DisplayRefreshRatePolicy.roundRefreshRate(165.0f));
        assertEquals(240, DisplayRefreshRatePolicy.roundRefreshRate(239.76f));
    }

    @Test
    public void invalidRateHasNoSyntheticSixtyHertzFallback() {
        assertEquals(0, DisplayRefreshRatePolicy.roundRefreshRate(0f));
        assertEquals(0, DisplayRefreshRatePolicy.roundRefreshRate(Float.NaN));
    }
}
