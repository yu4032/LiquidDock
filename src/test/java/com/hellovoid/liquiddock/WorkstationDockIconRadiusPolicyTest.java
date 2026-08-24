package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WorkstationDockIconRadiusPolicyTest {
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
