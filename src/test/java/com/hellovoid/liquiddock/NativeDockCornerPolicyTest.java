package com.hellovoid.liquiddock;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeDockCornerPolicyTest {
    @Test public void initialDockUsesRoundedCapsuleFromMeasuredSize() {
        assertEquals(40f, NativeDockCornerPolicy.provisionalRadius(420, 80, false), 0.0001f);
        assertEquals(35f, NativeDockCornerPolicy.provisionalRadius(70, 400, false), 0.0001f);
    }

    @Test public void unmeasuredDockNeverCommitsRectangularFallback() {
        assertTrue(Float.isNaN(NativeDockCornerPolicy.provisionalRadius(0, 80, false)));
        assertTrue(Float.isNaN(NativeDockCornerPolicy.provisionalRadius(420, 0, false)));
        assertTrue(Float.isNaN(NativeDockCornerPolicy.provisionalRadius(-1, 80, false)));
    }

    @Test public void onlyAllZeroBlurRegionsMayBeInitialized() {
        assertTrue(NativeDockCornerPolicy.hasRectangularStartupCorners(
                new float[]{0f, 0f, 0f, 0f}));
        assertFalse(NativeDockCornerPolicy.hasRectangularStartupCorners(
                new float[]{115f, 115f, 115f, 115f}));
        assertFalse(NativeDockCornerPolicy.hasRectangularStartupCorners(
                new float[]{115f, 0f, 0f, 0f}));
        assertFalse(NativeDockCornerPolicy.hasRectangularStartupCorners(
                new float[]{Float.NaN, 0f, 0f, 0f}));
        assertFalse(NativeDockCornerPolicy.hasRectangularStartupCorners(null));
    }

    @Test public void workstationTaskbarIsNeverForcedIntoRoundedCapsule() {
        assertTrue(Float.isNaN(NativeDockCornerPolicy.provisionalRadius(420, 80, true)));
    }
}
