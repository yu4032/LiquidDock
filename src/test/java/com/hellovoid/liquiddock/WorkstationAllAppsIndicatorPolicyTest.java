package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WorkstationAllAppsIndicatorPolicyTest {
    @Test public void portraitAndLandscapeUseIndependentOffsets() {
        assertEquals(127f, WorkstationAllAppsIndicatorPolicy.targetY(
                120f, true, true, true, -12, 7), 0.001f);
        assertEquals(108f, WorkstationAllAppsIndicatorPolicy.targetY(
                120f, true, true, false, -12, 7), 0.001f);
    }

    @Test public void normalHomeAndInactiveWorkstationKeepVendorBaseline() {
        assertEquals(120f, WorkstationAllAppsIndicatorPolicy.targetY(
                120f, true, false, true, -12, 7), 0.001f);
        assertEquals(120f, WorkstationAllAppsIndicatorPolicy.targetY(
                120f, false, true, false, -12, 7), 0.001f);
    }

    @Test public void repeatedRepositioningUsesVendorBaselineNotPreviousOffset() {
        assertEquals(110f, WorkstationAllAppsIndicatorPolicy.targetY(
                120f, true, true, false, -10, 10), 0.001f);
        assertEquals(100f, WorkstationAllAppsIndicatorPolicy.targetY(
                110f, true, true, false, -10, 10), 0.001f);
    }
}
