package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WorkstationDockIconRadiusPolicyTest {
    @Test public void configuredRadiusTracksTheRenderedIconScaleInBothOrientations() {
        assertEquals(24f, WorkstationDockIconRadiusPolicy.renderedRadius(
                16f, 2f, 166f, 166f, 124.5f, 124.5f), .001f);
        assertEquals(14.099f, WorkstationDockIconRadiusPolicy.renderedRadius(
                16f, 2f, 166f, 166f, 73.139f, 73.139f), .002f);
    }
    @Test public void positiveAbsoluteDpOverridesOnlyWorkstationDockIcons() {
        assertEquals(18f,
                WorkstationDockIconRadiusPolicy.resolve(52f, 18f, 2f, true), 0.001f);
        assertEquals(52f,
                WorkstationDockIconRadiusPolicy.resolve(52f, 18f, 2f, false), 0.001f);
    }

    @Test public void zeroKeepsConfiguredOrAutomaticIconRadius() {
        assertEquals(52f,
                WorkstationDockIconRadiusPolicy.resolve(52f, 0f, 2f, true), 0.001f);
    }
}
