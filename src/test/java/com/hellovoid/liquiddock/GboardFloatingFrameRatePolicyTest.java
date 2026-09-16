package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GboardFloatingFrameRatePolicyTest {
    @Test public void preservesHighRefreshDisplayRate() {
        assertEquals(120f, GboardFloatingFrameRatePolicy.preferredHz(120f), 0f);
    }

    @Test public void fallsBackWhenDisplayRateIsInvalid() {
        assertEquals(60f, GboardFloatingFrameRatePolicy.preferredHz(Float.NaN), 0f);
        assertEquals(60f, GboardFloatingFrameRatePolicy.preferredHz(0f), 0f);
    }
}
