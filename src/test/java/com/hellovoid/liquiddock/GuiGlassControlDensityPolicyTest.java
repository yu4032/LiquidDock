package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GuiGlassControlDensityPolicyTest {
    @Test public void shortGlassGroupsRetainPrismalSliders() {
        assertFalse(GuiGlassControlDensityPolicy.useLightweightTrack(0));
        assertFalse(GuiGlassControlDensityPolicy.useLightweightTrack(1));
        assertFalse(GuiGlassControlDensityPolicy.useLightweightTrack(4));
    }

    @Test public void denselyPopulatedGlassGroupsUseNativeTracks() {
        assertTrue(GuiGlassControlDensityPolicy.useLightweightTrack(5));
        assertTrue(GuiGlassControlDensityPolicy.useLightweightTrack(10));
        assertTrue(GuiGlassControlDensityPolicy.useLightweightTrack(40));
    }
}
