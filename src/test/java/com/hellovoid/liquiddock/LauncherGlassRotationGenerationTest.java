package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Rotation capture must respect the vendor-scaled settle delay used by production. */
public class LauncherGlassRotationGenerationTest {
    @Test public void rotationCaptureWaitsForVendorScaledTransitionSettle() {
        assertEquals(500L, LauncherGlassRotationSettlePolicy.settleDelayMs(1.0f));
        assertEquals(250L, LauncherGlassRotationSettlePolicy.settleDelayMs(0.5f));
        assertEquals(0L, LauncherGlassRotationSettlePolicy.settleDelayMs(0.0f));
        assertEquals(0L, LauncherGlassRotationSettlePolicy.settleDelayMs(-1.0f));
        assertEquals(500L, LauncherGlassRotationSettlePolicy.settleDelayMs(2.0f));
    }
}
